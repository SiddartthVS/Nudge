package com.nudge;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Manages accessibility events to track active applications, monitor scroll
 * counts,
 * and toggle a floating overlay HUD.
 * 
 * Variables:
 * - monitoredApps: Target packages for HUD and scroll tracking.
 * - handler / prefs: Main thread handler and SharedPreferences for local
 * storage.
 * - windowManager / hudView / layoutParams: Floating window components.
 * - isAttached / activeApp / eventVersion: Window and state tracking flags.
 * 
 * Functions:
 * 
 * [Lifecycle]
 * - onServiceConnected: Initializes window manager, HUD, and SharedPreferences.
 * - onInterrupt / onDestroy: Cleans up views, handlers, and resources.
 * 
 * [UI Management]
 * - initHud / attachHud / showHud / hideHud: Lifecycle and visibility
 * management for the overlay.
 * 
 * [Event Handling]
 * - onAccessibilityEvent: Intercepts scrolls (to track counts) and window
 * changes (to toggle HUD).
 * 
 * [Data Tracking]
 * - trackScroll: Increments scroll count for monitored apps in
 * SharedPreferences.
 * - syncDailyData: Validates the date, archives old data to DB, and resets
 * daily counts.
 * 
 * [Utilities]
 * - getEmptyDataMap: Generates a zeroed-out JSON state for monitored apps.
 * - getTodayDate: Retrieves the current date string (yyyy-MM-dd).
 */
public class TrackerService extends AccessibilityService {

    private final Set<String> monitoredApps = new HashSet<>(Arrays.asList(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private WindowManager windowManager;
    private View hudView;
    private WindowManager.LayoutParams layoutParams;

    private boolean isAttached;
    private String activeApp;
    private int eventVersion;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        prefs = getSharedPreferences("NudgePrefs", MODE_PRIVATE);

        handler.post(() -> {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            initHud();
            hideHud();
        });
    }

    private void initHud() {
        if (windowManager == null || hudView != null)
            return;

        hudView = LayoutInflater.from(this)
                .inflate(R.layout.floating_hud, null);

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        layoutParams.y = 100;

        hudView.setVisibility(View.GONE);
    }

    private boolean attachHud() {
        if (windowManager == null)
            return false;

        initHud();

        if (hudView == null)
            return false;

        if (isAttached)
            return true;

        try {
            windowManager.addView(hudView, layoutParams);
            isAttached = true;
            return true;
        } catch (Exception e) {
            Log.e("TrackerService", "Failed to add HUD view to WindowManager", e);
            isAttached = false;
            return false;
        }
    }

    private void showHud() {
        if (!attachHud())
            return;

        hudView.setVisibility(View.VISIBLE);
    }

    private void hideHud() {
        if (hudView != null)
            hudView.setVisibility(View.GONE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence name = event.getPackageName();
        if (name == null)
            return;

        String app = name.toString();
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            trackScroll(app);
            return;
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        if (app.equals("com.android.systemui") ||
                app.equals("com.android.inputmethod.latin") ||
                app.equals(getPackageName())) {
            return;
        }

        if (app.equals(activeApp))
            return;

        activeApp = app;
        int currentVersion = ++eventVersion;

        handler.postDelayed(() -> {
            if (currentVersion != eventVersion)
                return;

            if (monitoredApps.contains(app))
                showHud();
            else
                hideHud();
        }, 50);
    }

    private void trackScroll(String app) {
        if (!monitoredApps.contains(app))
            return;

        syncDailyData();

        try {
            String jsonStr = prefs.getString("scroll_data", getEmptyDataMap());
            JSONObject json = new JSONObject(jsonStr);

            int currentCount = json.optInt(app, 0);
            json.put(app, currentCount + 1);

            prefs.edit().putString("scroll_data", json.toString()).apply();
        } catch (Exception e) {
            Log.e("TrackerService", "Failed to parse or update scroll data", e);
        }
    }

    private void syncDailyData() {
        String today = getTodayDate();
        String savedDate = prefs.getString("current_date", null);

        if (savedDate == null) {
            prefs.edit()
                    .putString("current_date", today)
                    .putString("scroll_data", getEmptyDataMap())
                    .apply();
            return;
        }

        if (!savedDate.equals(today)) {
            String oldData = prefs.getString("scroll_data", getEmptyDataMap());

            new Thread(() -> {
                try {
                    ExternalDatabaseHandler.saveData(savedDate, oldData);
                } catch (Exception e) {
                    Log.e("TrackerService", "Failed to sync daily data to database", e);
                }
            }).start();

            prefs.edit()
                    .putString("current_date", today)
                    .putString("scroll_data", getEmptyDataMap())
                    .apply();
        }
    }

    private String getEmptyDataMap() {
        JSONObject json = new JSONObject();
        try {
            for (String app : monitoredApps) {
                json.put(app, 0);
            }
        } catch (Exception e) {
            Log.e("TrackerService", "Failed to create empty data map", e);
        }
        return json.toString();
    }

    private String getTodayDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    @Override
    public void onInterrupt() {
        handler.post(this::hideHud);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        handler.post(() -> {
            if (windowManager != null && hudView != null && isAttached) {
                try {
                    windowManager.removeViewImmediate(hudView);
                } catch (Exception e) {
                    Log.e("TrackerService", "Failed to remove HUD view on destroy", e);
                }
            }

            hudView = null;
            windowManager = null;
            isAttached = false;
            activeApp = null;
        });

        super.onDestroy();
    }
}