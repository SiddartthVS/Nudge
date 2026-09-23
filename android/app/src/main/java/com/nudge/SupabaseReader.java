package com.nudge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Reads scroll history back from Supabase. Calls the get_daily_counts Postgres function
 * (supabase/migrations/20260921000000_add_get_daily_counts.sql) and combines the result with
 * whatever is still only on-device (pending uploads, today's live counter) so totals are as
 * complete as possible even when the network is slow or offline.
 */
final class SupabaseReader {

    private static final String TAG = "NudgeSupabaseRead";
    private static final String RPC_DAILY_COUNTS = "get_daily_counts";
    private static final int CACHE_DAYS = 7;

    // How long to wait before trying another background refresh, depending on whether the
    // last one worked.
    private static final long REFRESH_OK_INTERVAL_MS = 10 * 60 * 1000L;
    private static final long REFRESH_FAIL_INTERVAL_MS = 60 * 1000L;

    private static final Object REFRESH_LOCK = new Object();
    private static boolean refreshing = false;
    private static long nextRefreshAt = 0L;

    private SupabaseReader() {
    }

    /**
     * Fetches the last CACHE_DAYS days from Supabase and merges them into the local cache
     * (WeekHistoryStore), so the "This week" chart stays up to date. Safe to call often - it
     * no-ops if a refresh is already running or one ran recently.
     */
    static void refreshRecentDaysAsync(Context context) {
        final Context app = context.getApplicationContext();
        long now = SystemClock.elapsedRealtime();

        synchronized (REFRESH_LOCK) {
            if (refreshing || now < nextRefreshAt) {
                return;
            }
            refreshing = true;
        }

        new Thread(() -> {
            boolean succeeded = false;
            try {
                SupabaseSync.retryPending(app);
                JSONArray rows = fetchDailyCounts(app, dateDaysAgo(CACHE_DAYS));
                WeekHistoryStore.mergeFetchedDays(app, rows);
                succeeded = true;
                Log.i(TAG, "REFRESHED | " + rows.length() + " days");
            } catch (Exception e) {
                Log.w(TAG, "REFRESH FAILED | " + e);
            } finally {
                synchronized (REFRESH_LOCK) {
                    refreshing = false;
                    nextRefreshAt = SystemClock.elapsedRealtime()
                            + (succeeded ? REFRESH_OK_INTERVAL_MS : REFRESH_FAIL_INTERVAL_MS);
                }
            }
        }, "nudge-supabase-refresh").start();
    }

    /**
     * Sums every app's counts over the last `days` days into one total per app. Combines three
     * sources so the number is as accurate as possible: Supabase, anything still queued for
     * upload, and today's live counter. Falls back to the on-device 7-day cache (marked
     * "complete": false) if Supabase can't be reached.
     */
    static JSONObject getRangeTotals(Context context, int days) throws Exception {
        String from = days > 0 ? dateDaysAgo(days - 1) : null;
        TreeMap<String, JSONObject> byDay = new TreeMap<>();
        boolean complete = true;

        // days == 1 ("Today") never needs Supabase - the live counter below already has it.
        if (days != 1) {
            try {
                JSONArray rows = fetchDailyCounts(context, from);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    byDay.put(row.getString("day"), row.getJSONObject("counts"));
                }
            } catch (Exception e) {
                Log.w(TAG, "RANGE FETCH FAILED | " + e);
                complete = false;
                putDays(byDay, WeekHistoryStore.readCache(context), from);
            }
        }

        // Add anything not yet confirmed uploaded, so it isn't missing from the total.
        JSONObject pending;
        synchronized (SupabaseSync.LOCK) {
            pending = SupabaseSync.readPending(context);
        }
        Iterator<String> pendingDates = pending.keys();
        while (pendingDates.hasNext()) {
            String date = pendingDates.next();
            if (inRange(date, from)) {
                byDay.put(date, new JSONObject(pending.getString(date)));
            }
        }

        // Add today's still-accumulating count, which hasn't been archived yet.
        SharedPreferences prefs =
                context.getSharedPreferences(TrackerService.PREFS_NAME, Context.MODE_PRIVATE);
        String liveDate = prefs.getString(TrackerService.KEY_CURRENT_DATE, null);
        String liveCounts = prefs.getString(TrackerService.KEY_SCROLL_DATA, null);
        if (liveDate != null && liveCounts != null && inRange(liveDate, from)) {
            byDay.put(liveDate, new JSONObject(liveCounts));
        }

        JSONObject totals = new JSONObject();
        for (JSONObject counts : byDay.values()) {
            Iterator<String> packages = counts.keys();
            while (packages.hasNext()) {
                String pkg = packages.next();
                totals.put(pkg, totals.optInt(pkg, 0) + counts.optInt(pkg, 0));
            }
        }

        JSONObject result = new JSONObject();
        result.put("totals", totals);
        result.put("complete", complete);
        return result;
    }

    /** Calls the get_daily_counts RPC and returns its rows as-is: [{ "day": ..., "counts": ... }]. */
    static JSONArray fetchDailyCounts(Context context, String fromDate) throws Exception {
        if (!SupabaseSync.isConfigured()) {
            throw new IOException("Supabase is not configured");
        }

        JSONObject body = new JSONObject();
        body.put("p_device_id", SupabaseSync.getDeviceId(context));
        body.put("p_from", fromDate == null ? JSONObject.NULL : fromDate);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = null;
        try {
            conn = SupabaseSync.openRpc(RPC_DAILY_COUNTS, payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " " + SupabaseSync.readBody(conn.getErrorStream()));
            }
            return new JSONArray(SupabaseSync.readBody(conn.getInputStream()));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void putDays(TreeMap<String, JSONObject> target, JSONObject days, String from)
            throws Exception {
        Iterator<String> dates = days.keys();
        while (dates.hasNext()) {
            String date = dates.next();
            if (inRange(date, from)) {
                target.put(date, days.getJSONObject(date));
            }
        }
    }

    private static boolean inRange(String date, String from) {
        return from == null || date.compareTo(from) >= 0;
    }

    private static String dateDaysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }
}
