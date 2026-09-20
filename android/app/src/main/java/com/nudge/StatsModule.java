package com.nudge;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

/**
 * Bridges the on-device scroll history (see WeekHistoryStore) to the React Native UI.
 * Adds no tracking logic of its own - it only exposes what TrackerService already persists.
 */
public class StatsModule extends ReactContextBaseJavaModule {

    StatsModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "StatsModule";
    }

    /**
     * Resolves a JSON string shaped as:
     *   { "yyyy-MM-dd": { "<package>": <count>, ... }, ... }
     * covering roughly the last week, including today's still-accumulating count.
     *
     * Returned as a raw JSON string rather than a WritableMap: the per-app keys are package
     * names, not fixed field names, so a string the JS side parses with JSON.parse avoids
     * writing (and maintaining) a manual JSONObject -> WritableMap conversion for no benefit.
     */
    @ReactMethod
    public void getWeekHistory(Promise promise) {
        try {
            String json = WeekHistoryStore
                    .readRecentDaysIncludingToday(getReactApplicationContext())
                    .toString();
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("STATS_READ_FAILED", e);
        }
    }
}
