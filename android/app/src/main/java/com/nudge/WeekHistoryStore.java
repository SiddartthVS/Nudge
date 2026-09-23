package com.nudge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * On-device history of the last 7 finished days, so the "This week" chart has something fast
 * and offline-friendly to read. Supabase (SupabaseSync/SupabaseReader) is the permanent copy;
 * this is a local cache of it, refreshed whenever SupabaseReader fetches new data.
 *
 * Stored as one JSON object in the same SharedPreferences file TrackerService uses:
 *   { "yyyy-MM-dd": { "<package>": <count>, ... }, ... }
 */
final class WeekHistoryStore {

    private static final String TAG = "NudgeWeekHistory";
    private static final String KEY_HISTORY = "week_history";
    private static final int MAX_DAYS_KEPT = 7;

    private WeekHistoryStore() {
    }

    /** Called once per finished day, right when TrackerService archives it. */
    static synchronized void recordDay(Context context, String date, String jsonCounts) {
        SharedPreferences prefs = prefs(context);
        try {
            JSONObject history = readHistory(prefs);
            history.put(date, new JSONObject(jsonCounts));
            pruneToRecent(history, MAX_DAYS_KEPT);
            prefs.edit().putString(KEY_HISTORY, history.toString()).apply();
            Log.i(TAG, "RECORDED | " + date);
        } catch (Exception e) {
            Log.e(TAG, "recordDay failed for " + date, e);
        }
    }

    /**
     * Local history plus today's still-accumulating count, read straight from TrackerService's
     * live counters so "today" is always current, not stale until it's archived at midnight.
     */
    static JSONObject readRecentDaysIncludingToday(Context context) {
        SharedPreferences prefs = prefs(context);
        JSONObject result = readHistory(prefs);

        try {
            String today = prefs.getString(TrackerService.KEY_CURRENT_DATE, null);
            String todayCounts = prefs.getString(TrackerService.KEY_SCROLL_DATA, null);
            if (today != null && todayCounts != null) {
                result.put(today, new JSONObject(todayCounts));
            }
        } catch (Exception e) {
            Log.e(TAG, "Merging today's live counts failed", e);
        }

        return result;
    }

    /** Merges rows fetched from Supabase (SupabaseReader) into the local cache. */
    static synchronized void mergeFetchedDays(Context context, JSONArray rows) {
        SharedPreferences prefs = prefs(context);
        try {
            JSONObject history = readHistory(prefs);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                history.put(row.getString("day"), row.getJSONObject("counts"));
            }
            pruneToRecent(history, MAX_DAYS_KEPT);
            prefs.edit().putString(KEY_HISTORY, history.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "mergeFetchedDays failed", e);
        }
    }

    static JSONObject readCache(Context context) {
        return readHistory(prefs(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(TrackerService.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONObject readHistory(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_HISTORY, null);
        if (raw == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "History was corrupt, resetting it", e);
            return new JSONObject();
        }
    }

    /** Keeps only the most recent `keep` dates ("yyyy-MM-dd" sorts correctly as plain text). */
    private static void pruneToRecent(JSONObject history, int keep) throws Exception {
        List<String> dates = new ArrayList<>();
        Iterator<String> keys = history.keys();
        while (keys.hasNext()) {
            dates.add(keys.next());
        }
        if (dates.size() <= keep) {
            return;
        }
        Collections.sort(dates);
        int removeCount = dates.size() - keep;
        for (int i = 0; i < removeCount; i++) {
            history.remove(dates.get(i));
        }
    }
}
