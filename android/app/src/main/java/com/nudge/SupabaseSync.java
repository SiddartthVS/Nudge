package com.nudge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Uploads finished days to Supabase (Postgres).
 *
 * WHY THIS IS PLAIN HTTP AND NOT supabase-js
 * TrackerService is a native Java AccessibilityService. It runs whether or not
 * the React Native
 * JS engine is alive, so it cannot call the supabase-js package that is
 * installed for the JS
 * side. Instead this class talks straight to Supabase's REST API with
 * HttpURLConnection, which
 * needs no extra Gradle dependency.
 *
 * WHAT IT CALLS
 * POST {SUPABASE_URL}/rest/v1/rpc/upsert_daily_counts - a Postgres function
 * created by
 * supabase/migrations/20260919000000_create_daily_scroll_counts.sql. It writes
 * one row per
 * (device, day) into public.daily_scroll_counts and overwrites that row if it
 * already exists,
 * so uploading the same day twice is harmless.
 *
 * RELIABILITY
 * Days are archived exactly once (at the daily rollover), and by then the local
 * counters have
 * already been reset. So a failed upload (no internet, Supabase down) must NOT
 * lose the day:
 * every day is first written to a small pending queue in SharedPreferences, and
 * only removed
 * once Supabase confirms it. Anything left in the queue is retried on the next
 * archive and every
 * time the service starts (see retryPending).
 *
 * Every public method is safe to call from the main thread; the work runs on a
 * background thread.
 */
public final class SupabaseSync {

    private static final String TAG = "NudgeSupabase";

    // ============================================================================================
    // PLACEHOLDERS - replace these two values (Supabase dashboard > Project
    // Settings > API Keys).
    //
    // SUPABASE_URL : "Project URL", e.g. https://abcdefghijklmnop.supabase.co
    // SUPABASE_KEY : the PUBLISHABLE key (sb_publishable_...) or the legacy "anon"
    // key (eyJ...).
    //
    // NEVER put the secret key or the service_role key here - anything in the APK
    // can be
    // extracted. The publishable/anon key is designed to be public: the migration
    // locks the table
    // so this key can only call upsert_daily_counts and cannot read or edit rows
    // directly.
    // ============================================================================================
    private static final String SUPABASE_URL = "https://zxchoyxqusfbahiidzan.supabase.co/rest/v1/";
    private static final String SUPABASE_KEY = "sb_publishable_5Ah8GtyZbGh_sq7svj98HA_olaKeAbU";

    private static final String RPC_PATH = "/rest/v1/rpc/upsert_daily_counts";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    // Same prefs file TrackerService uses; different keys, so no clash.
    private static final String PREFS_NAME = "NudgePrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PENDING = "pending_uploads";

    private enum Result {
        OK, RETRY_LATER, DROP
    }

    /**
     * Serialises queue reads/writes and uploads so two threads can never race on
     * the queue.
     */
    private static final Object LOCK = new Object();

    private SupabaseSync() {
    }

    // ---------------------------------------------------------------------- public
    // API

    /**
     * Queue a finished day and try to upload everything pending.
     *
     * @param date     "yyyy-MM-dd" (the device's local date for the day being
     *                 archived)
     * @param jsonData per-app counts, e.g.
     *                 {"com.instagram.android":214,"com.google.android.youtube":37}
     */
    public static void archiveDay(final Context context, final String date, final String jsonData) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    try {
                        enqueue(app, date, jsonData);
                        flushPending(app);
                    } catch (Exception e) {
                        Log.e(TAG, "archiveDay failed for " + date, e);
                    }
                }
            }
        }, "nudge-supabase").start();
    }

    /**
     * Retry anything left over from earlier failures. Cheap no-op when the queue is
     * empty.
     */
    public static void retryPending(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    try {
                        flushPending(app);
                    } catch (Exception e) {
                        Log.e(TAG, "retryPending failed", e);
                    }
                }
            }
        }, "nudge-supabase-retry").start();
    }

    // ------------------------------------------------------------------------
    // queue

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONObject readPending(Context context) {
        String raw = prefs(context).getString(KEY_PENDING, null);
        if (raw == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "Pending queue was corrupt, resetting it", e);
            return new JSONObject();
        }
    }

    /**
     * commit(), not apply(): we are on a background thread and must be durable
     * before uploading.
     */
    private static void writePending(Context context, JSONObject pending) {
        prefs(context).edit().putString(KEY_PENDING, pending.toString()).commit();
    }

    private static void enqueue(Context context, String date, String jsonData) throws Exception {
        JSONObject pending = readPending(context);
        pending.put(date, jsonData);
        writePending(context, pending);
        Log.i(TAG, "QUEUED | " + date);
    }

    /** Must be called while holding LOCK. */
    private static void flushPending(Context context) throws Exception {
        if (!isConfigured()) {
            Log.w(TAG, "Supabase URL/key are still placeholders - days stay queued on the device.");
            return;
        }

        JSONObject pending = readPending(context);
        if (pending.length() == 0) {
            return;
        }

        String deviceId = getDeviceId(context);

        List<String> dates = new ArrayList<>();
        Iterator<String> keys = pending.keys();
        while (keys.hasNext()) {
            dates.add(keys.next());
        }
        // Oldest first (yyyy-MM-dd sorts correctly as text).
        java.util.Collections.sort(dates);

        for (String date : dates) {
            Result result = upload(deviceId, date, pending.optString(date, "{}"));

            if (result == Result.OK || result == Result.DROP) {
                pending.remove(date);
                writePending(context, pending);
            } else {
                // Offline or server trouble: stop now, everything left stays queued.
                break;
            }
        }
    }

    // ----------------------------------------------------------------------
    // network

    private static Result upload(String deviceId, String date, String jsonData) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("p_device_id", deviceId);
            body.put("p_day", date);
            body.put("p_counts", new JSONObject(jsonData));
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            conn = (HttpURLConnection) new URL(SUPABASE_URL + RPC_PATH).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payload.length);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", SUPABASE_KEY);
            // Legacy anon keys are JWTs and also go in Authorization. New publishable keys
            // (sb_publishable_...) are not JWTs and must only be sent as "apikey".
            if (SUPABASE_KEY.startsWith("eyJ")) {
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            }

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                Log.i(TAG, "UPLOADED | " + date + " | HTTP " + status);
                return Result.OK;
            }

            String error = readBody(conn.getErrorStream());
            Log.e(TAG, "UPLOAD FAILED | " + date + " | HTTP " + status + " | " + error);

            // 400/422 = the payload itself is rejected; retrying can never fix that, and
            // keeping
            // it would block every later day behind it. Everything else (401/403 bad key,
            // 404 wrong
            // URL or migration not run, 5xx, 429) is fixable, so keep the day queued.
            return (status == 400 || status == 422) ? Result.DROP : Result.RETRY_LATER;

        } catch (Exception e) {
            // Timeout, no connectivity, DNS failure, etc.
            Log.w(TAG, "UPLOAD ERROR | " + date + " | " + e);
            return Result.RETRY_LATER;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readBody(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ----------------------------------------------------------------------
    // helpers

    private static boolean isConfigured() {
        return !SUPABASE_URL.contains("YOUR_") && !SUPABASE_KEY.contains("YOUR_");
    }

    /**
     * Anonymous per-install id, so this device's days stay grouped together in the
     * table without
     * needing a login. Note: uninstalling the app creates a new id.
     */
    private static String getDeviceId(Context context) {
        SharedPreferences p = prefs(context);
        String id = p.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            p.edit().putString(KEY_DEVICE_ID, id).commit();
        }
        return id;
    }
}
