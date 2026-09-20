package com.nudge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Local, on-device history of finished days, so the "This week" chart has real data to read
 * without depending on Supabase. Supabase (see SupabaseSync) is write-only by design: the
 * migration enables RLS on daily_scroll_counts with no select policy, so the app's own key
 * can never read it back - only the upsert function can write to it. This class is the actual
 * read path for the UI.
 *
 * Stored as one JSON object under a single SharedPreferences key, in the same prefs file
 * TrackerService already uses:
 *   { "yyyy-MM-dd": { "<package>": <count>, ... }, ... }
 * capped to the most recent MAX_DAYS_KEPT days so it can never grow unbounded.
 */
final class WeekHistoryStore {

    private static final String TAG = "NudgeWeekHistory";
    private static final String KEY_HISTORY = "week_history";

    /** A week plus a little slack for timezone/day-boundary edge cases. */
    private static final int MAX_DAYS_KEPT = 9;

    private WeekHistoryStore() {
    }

    /** Called once per finished day, right when TrackerService archives it. */
    static void recordDay(Context context, String date, String jsonCounts) {
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
     * Finished days from local history, plus today's still-accumulating count read straight
     * from TrackerService's own live counters - so "today" in the chart is always current,
     * not stale until midnight's archive() call finally records it.
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
