package com.nudge;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

/**
 * Bridges the on-device scroll history to the React Native UI. Holds no tracking logic of
 * its own - it only reads what TrackerService/WeekHistoryStore/SupabaseReader already produce.
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
     * Resolves a JSON string shaped as { "yyyy-MM-dd": { "<package>": <count>, ... }, ... },
     * covering roughly the last 7 days including today's still-live count. Also kicks off a
     * background refresh from Supabase so later calls have fresher data.
     */
    @ReactMethod
    public void getWeekHistory(Promise promise) {
        SupabaseReader.refreshRecentDaysAsync(getReactApplicationContext());
        try {
            String json = WeekHistoryStore
                    .readRecentDaysIncludingToday(getReactApplicationContext())
                    .toString();
            promise.resolve(json);
        } catch (Exception e) {
            promise.reject("STATS_READ_FAILED", e);
        }
    }

    /**
     * Total per-app counts over the last `days` days (1 = today only). Runs on a background
     * thread since it may call Supabase over the network.
     */
    @ReactMethod
    public void getRangeTotals(final double days, final Promise promise) {
        final ReactApplicationContext context = getReactApplicationContext();
        new Thread(() -> {
            try {
                promise.resolve(SupabaseReader.getRangeTotals(context, (int) days).toString());
            } catch (Exception e) {
                promise.reject("STATS_RANGE_FAILED", e);
            }
        }, "nudge-range-stats").start();
    }
}
