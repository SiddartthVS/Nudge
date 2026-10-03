package com.nudge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which monitored apps are currently "blocked" - meaning TrackerService should immediately
 * perform a back gesture every time it counts a reel from that app, instead of just counting it.
 *
 * Set from the React Native side (BlockModule, via the two Blockbutton toggles on the home
 * screen) and read from TrackerService on its own thread, so this is kept intentionally simple
 * and safe to call from either side: a toggle writes straight to disk (no debounce - a user
 * flipping this switch wants it to take effect on the very next scroll), and an in-memory copy
 * is kept so TrackerService's check on every counted reel never has to touch disk.
 */
final class BlockState {

    private static final String TAG = "NudgeBlockState";
    private static final String KEY_BLOCKED_APPS = "blocked_apps";

    /** In-memory mirror of what's on disk. Replaced wholesale on every change (never mutated
     *  in place), so a read on one thread can never see a half-written set. */
    private static volatile Set<String> blockedApps;

    private BlockState() {
    }

    /** Cheap, safe to call on every counted reel. */
    static boolean isBlocked(Context context, String pkg) {
        return current(context).contains(pkg);
    }

    /** Called when the user taps or holds a Blockbutton. Takes effect immediately. */
    static synchronized void setBlocked(Context context, String pkg, boolean blocked) {
        Set<String> next = new HashSet<>(current(context));
        if (blocked) {
            next.add(pkg);
        } else {
            next.remove(pkg);
        }
        blockedApps = Collections.unmodifiableSet(next);
        persist(context, next);
        Log.i(TAG, (blocked ? "BLOCKED | " : "UNBLOCKED | ") + pkg);
    }

    /** Loads from disk once, on first use after the process starts; free after that. */
    private static Set<String> current(Context context) {
        Set<String> loaded = blockedApps;
        if (loaded != null) {
            return loaded;
        }
        synchronized (BlockState.class) {
            if (blockedApps == null) {
                blockedApps = Collections.unmodifiableSet(readFromDisk(context));
            }
            return blockedApps;
        }
    }

    private static Set<String> readFromDisk(Context context) {
        String raw = prefs(context).getString(KEY_BLOCKED_APPS, null);
        Set<String> result = new HashSet<>();
        if (raw == null) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "Blocked-apps list was corrupt, starting empty", e);
        }
        return result;
    }

    private static void persist(Context context, Set<String> apps) {
        JSONArray array = new JSONArray();
        for (String pkg : apps) {
            array.put(pkg);
        }
        prefs(context).edit().putString(KEY_BLOCKED_APPS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(TrackerService.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
