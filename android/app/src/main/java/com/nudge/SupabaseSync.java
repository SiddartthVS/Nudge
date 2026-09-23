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
 * Uploads finished days to Supabase over plain HTTP (not the supabase-js package, since this
 * runs inside the accessibility service, independent of whether the JS engine is alive).
 * Calls the upsert_daily_counts Postgres function - see
 * supabase/migrations/20260919000000_create_daily_scroll_counts.sql.
 *
 * Reliability: a finished day is written to a local pending queue BEFORE it's uploaded, and
 * only removed once Supabase confirms it. If the upload fails (offline, server error) the day
 * just stays queued and is retried on the next rollover or service start - see retryPending.
 *
 * Every public method here is safe to call from the main thread; the actual work always runs
 * on a background thread.
 */
public final class SupabaseSync {

    private static final String TAG = "NudgeSupabase";

    // ============================================================================
    // Replace these two before release (Supabase dashboard > Project Settings > API Keys).
    // SUPABASE_URL = "Project URL". SUPABASE_KEY = the PUBLISHABLE or anon key - never the
    // secret/service_role key, since anything in the APK can be extracted. The table's RLS
    // policy means this key can only call upsert_daily_counts, never read/write rows directly.
    // ============================================================================
    private static final String SUPABASE_URL = "https://zxchoyxqusfbahiidzan.supabase.co";
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

    /** Serialises queue reads/writes/uploads so two threads never race on the queue. */
    static final Object LOCK = new Object();

    private SupabaseSync() {
    }

    // ---------------------------------------------------------------------- public API

    /**
     * Queues a finished day and tries to upload everything pending.
     *
     * @param date     "yyyy-MM-dd", the device's local date for the day being archived
     * @param jsonData per-app counts, e.g. {"com.instagram.android":214,...}
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

    /** Retries anything left over from earlier failures. Cheap no-op if the queue is empty. */
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

    // ------------------------------------------------------------------------ queue

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static JSONObject readPending(Context context) {
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

    /** commit(), not apply(): this runs on a background thread and must be durable before upload. */
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
        // Oldest first ("yyyy-MM-dd" sorts correctly as text).
        java.util.Collections.sort(dates);

        for (String date : dates) {
            Result result = upload(deviceId, date, pending.optString(date, "{}"));

            if (result == Result.OK || result == Result.DROP) {
                pending.remove(date);
                writePending(context, pending);
            } else {
                // Offline or server trouble - stop here, everything left stays queued.
                break;
            }
        }
    }

    // ---------------------------------------------------------------------- network

    /** Opens a POST connection to a Supabase RPC endpoint with the right auth headers. */
    static HttpURLConnection openRpc(String rpcName, int bodyLength) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/rpc/" + rpcName)
                .openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(bodyLength);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", SUPABASE_KEY);
        if (SUPABASE_KEY.startsWith("eyJ")) {
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
        }
        return conn;
    }

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
            // Legacy anon keys are JWTs and also need Authorization. New publishable keys
            // (sb_publishable_...) aren't JWTs and must only be sent as "apikey".
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

            // 400/422 = the payload itself is invalid; retrying can't fix that and would block
            // every later day behind it, so drop it. Everything else (bad key, wrong URL, 5xx,
            // rate limit) is fixable, so keep the day queued.
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

    static String readBody(InputStream in) {
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

    // ---------------------------------------------------------------------- helpers

    static boolean isConfigured() {
        return !SUPABASE_URL.contains("YOUR_") && !SUPABASE_KEY.contains("YOUR_");
    }

    /**
     * Anonymous per-install id that groups this device's days together in the table, without
     * needing a login. Uninstalling the app creates a new id on the next install.
     *
     * TODO BEFORE RELEASE: this currently ignores the real id below and always returns the
     * fixed string "TEST_DEVICE_DO_NOT_USE", so every install shares one row of test data
     * instead of getting its own private data. Change the final line to `return id;`.
     */
    static synchronized String getDeviceId(Context context) {
        SharedPreferences p = prefs(context);
        String id = p.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            p.edit().putString(KEY_DEVICE_ID, id).commit();
        }
        return "TEST_DEVICE_DO_NOT_USE";
    }
}
