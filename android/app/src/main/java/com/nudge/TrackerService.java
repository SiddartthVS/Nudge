package com.nudge;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NUDGE - DoomScroll counter.
 *
 * Counting rule: a scroll counts only when the on-screen reel actually changes,
 * not on every raw TYPE_VIEW_SCROLLED event. Android fires TYPE_VIEW_SCROLLED
 * repeatedly while a finger is dragging - including while the drag is held or
 * released back onto the same reel - so counting events 1:1 makes the counter
 * climb with nothing changing on screen. ReelSignal (ported from the open-source
 * Curbox project's reel counter) reads a small piece of on-screen text identifying
 * the current reel and only reports a change when it is genuinely different. See
 * ReelSignal.java for the full explanation.
 *
 * Data flow:
 *   candidate event -> ReelSignal decides if the reel changed -> increment in
 *   memory -> update HUD -> schedule a persist
 *
 * The in-memory increment and the HUD update are always immediate once ReelSignal
 * says a reel changed. Only the SharedPreferences write is delayed (trailing debounce).
 */
public class TrackerService extends AccessibilityService {

    private static final String TAG = "NudgeTracker";

    private static final String PREFS_NAME = "NudgePrefs";
    private static final String KEY_SCROLL_DATA = "scroll_data";
    private static final String KEY_CURRENT_DATE = "current_date";

    /** Trailing debounce for disk writes. Never delays the counter itself. */
    private static final long PERSIST_DELAY_MS = 1500L;

    /** Debounce before hiding, so a transient window does not flicker the HUD. */
    private static final long HIDE_DELAY_MS = 250L;

    /** HUD flash: fade in, stay fully visible, then fade out. */
    private static final long HUD_FADE_IN_MS = 150L;
    private static final long HUD_VISIBLE_MS = 2000L;
    private static final long HUD_FADE_OUT_MS = 400L;

    /** Font file inside android/app/src/main/assets. */
    private static final String HUD_FONT_ASSET = "fonts/WorkSans-Black.ttf";

    /** How often we bother recomputing today's date. */
    private static final long DATE_CHECK_INTERVAL_MS = 60_000L;

    /**
     * Floor between reel-content checks for the same app. Real reel transitions never
     * happen faster than this, so this just bounds how often we do node-tree work during
     * a fast fling or a burst of content-changed events - it does not add latency to
     * genuine counts, since two distinct reels are always further apart than this.
     */
    private static final long REEL_CHECK_DEBOUNCE_MS = 120L;
    private final Map<String, Long> lastReelCheckAt = new HashMap<>();

    /**
     * Trailing "settle" check. The throttle above drops events, and the event it drops is often
     * the LAST one of a swipe - the one that fires once the new reel is on screen. Without a
     * follow-up read, that reel was never seen and its count was lost. After the last event
     * for an app, we read the screen once more this long later.
     */
    private static final long REEL_SETTLE_DELAY_MS = 250L;
    private final Map<String, Runnable> settleRunnables = new HashMap<>();

    /**
     * Single source of truth for monitored packages. Nothing else in the project
     * declares these, so this stays the one place to edit them.
     */
    static final Set<String> MONITORED_APPS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "com.instagram.android",
                    "com.google.android.youtube",
                    "com.facebook.katana",
                    "com.snapchat.android")));

    private final Map<String, Integer> scrollCache = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;

    private WindowManager windowManager;
    private View hudView;
    private TextView counterText;
    private WindowManager.LayoutParams layoutParams;
    private boolean isAttached;

    private String currentApp;
    private String cachedDate;
    private long lastDateCheckAt;
    private boolean persistScheduled;

    private final ReelSignal reelSignal = new ReelSignal();

    private Typeface hudTypeface;

    private final Runnable fadeOutRunnable = new Runnable() {
        @Override
        public void run() {
            if (hudView != null) {
                hudView.animate().alpha(0f).setDuration(HUD_FADE_OUT_MS).start();
            }
        }
    };

    private final Runnable persistRunnable = new Runnable() {
        @Override
        public void run() {
            persistScheduled = false;
            persistNow();
        }
    };

    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            hideHud();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "SERVICE CONNECTED");

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        currentApp = null;
        isAttached = false;

        loadState();
        createHud();
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "SERVICE INTERRUPTED");
        persistNow();
        hideHud();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "SERVICE UNBOUND");
        teardown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SERVICE DESTROYED");
        teardown();
        super.onDestroy();
    }

    /**
     * Runs synchronously on the main thread. Doing this on a Handler post (as the
     * previous version did) meant super.onDestroy() had already run and the
     * overlay window leaked.
     */
    private void teardown() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(persistRunnable);
        for (Runnable settle : settleRunnables.values()) {
            handler.removeCallbacks(settle);
        }
        settleRunnables.clear();
        persistScheduled = false;

        persistNow();
        hideHud();

        hudView = null;
        counterText = null;
        layoutParams = null;
        windowManager = null;
    }

    // ------------------------------------------------------------------- events

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }

        String app = packageName.toString();
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (MONITORED_APPS.contains(app)) {
                handleReelCandidateEvent(app, event);
            }
            return;
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onForegroundWindow(app);
        }
    }

    /**
     * A scroll or content-change event from a monitored app MIGHT mean the user moved to a
     * new reel. Debounced per app, then handed to ReelSignal to decide for certain by reading
     * the actual on-screen content - see ReelSignal.isNewReel for why we can't just trust the
     * event itself.
     */
    private void handleReelCandidateEvent(String app, AccessibilityEvent event) {
        // A real scroll/content event for a monitored app can only exist if that app is
        // genuinely on screen right now - this is stronger evidence than any window-state
        // event. Fixes cases like a fingerprint/app-lock gate in front of Instagram: its
        // biometric prompt is correctly ignored as transient noise (see isTransientPackage),
        // but some OEM app-locks never re-fire a window-state event for Instagram once the
        // prompt clears, which used to leave the HUD stuck hidden even though counting itself
        // (keyed purely off this event's own package, below) was working the whole time.
        confirmMonitoredForeground(app);

        if (!reelSignal.isCandidateEvent(app, event.getEventType())) {
            return;
        }

        // Always schedule the trailing check, even if the throttle below skips this event.
        scheduleSettleCheck(app);

        long now = SystemClock.elapsedRealtime();
        Long lastCheck = lastReelCheckAt.get(app);
        if (lastCheck != null && now - lastCheck < REEL_CHECK_DEBOUNCE_MS) {
            return;
        }
        lastReelCheckAt.put(app, now);

        checkReel(app);
    }

    /** Re-arms the one pending settle check for this app (each new event pushes it back). */
    private void scheduleSettleCheck(final String app) {
        Runnable pending = settleRunnables.get(app);
        if (pending != null) {
            handler.removeCallbacks(pending);
        }
        Runnable settle = new Runnable() {
            @Override
            public void run() {
                settleRunnables.remove(app);
                // User may have left the app during the delay.
                if (app.equals(currentApp)) {
                    checkReel(app);
                }
            }
        };
        settleRunnables.put(app, settle);
        handler.postDelayed(settle, REEL_SETTLE_DELAY_MS);
    }

    private void checkReel(String app) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            CharSequence rootPkg = root.getPackageName();
            if (rootPkg == null || !app.contentEquals(rootPkg)) {
                return; // active window is something else (overlay, other app)
            }
            if (reelSignal.isNewReel(app, root)) {
                onReelCounted(app);
            }
        } finally {
            try {
                //noinspection deprecation
                root.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Bring the HUD in line with reality: if we're seeing events from a monitored app, that
     * app is on screen, full stop, regardless of what the last window-state event said (or
     * failed to say). Cheap - just a couple of field checks - so safe to call on every event,
     * not just ones that end up counting a reel.
     */
    private void confirmMonitoredForeground(String app) {
        handler.removeCallbacks(hideRunnable);
        if (app.equals(currentApp) && isAttached) {
            return;
        }
        currentApp = app;
        Log.i(TAG, "CURRENT APP = " + app + " | MONITORED = true (confirmed via scroll/content event)");
        showHud(app);
    }

    private void onReelCounted(String app) {
        maybeRollOverDate();

        int count = increment(app);
        Log.i(TAG, "REEL COUNTED | APP = " + app + " | COUNT = " + count);

        updateHud(app);
        flashHud();
        schedulePersist();
    }

    private void onForegroundWindow(String app) {
        if (isTransientPackage(app)) {
            Log.i(TAG, "WINDOW IGNORED | " + app);
            return;
        }

        boolean monitored = MONITORED_APPS.contains(app);

        // Nothing to do if we are already in this app and the HUD state is correct.
        if (app.equals(currentApp) && monitored == isAttached) {
            return;
        }

        currentApp = app;
        Log.i(TAG, "CURRENT APP = " + app + " | MONITORED = " + monitored);

        handler.removeCallbacks(hideRunnable);

        if (monitored) {
            showHud(app);
        } else {
            handler.postDelayed(hideRunnable, HIDE_DELAY_MS);
        }
    }

    /**
     * System UI, keyboards, biometric/lock prompts and Nudge itself push windows on top of
     * the app the user is actually looking at. Treating those as the foreground app would
     * make the HUD disappear mid-scroll. This list is intentionally loose (keyword matching,
     * not exact package names) because fingerprint/app-lock gates in front of a monitored app
     * are OEM-specific and vary; even when a gate's package slips through this filter, the
     * moment the gated app itself produces a real event, confirmMonitoredForeground corrects
     * the HUD immediately regardless. This filter mainly avoids visible flicker from noise,
     * not correctness.
     */
    private boolean isTransientPackage(String pkg) {
        return pkg.equals(getPackageName())
                || pkg.equals("com.android.systemui")
                || pkg.contains("inputmethod")
                || pkg.contains("keyboard")
                || pkg.contains("honeyboard")
                || pkg.contains("biometric")
                || pkg.contains("fingerprint")
                || pkg.contains("applock")
                || pkg.contains("app.lock")
                || pkg.contains("keyguard");
    }

    // ------------------------------------------------------------------ counter

    private int increment(String app) {
        Integer current = scrollCache.get(app);
        int next = (current == null ? 0 : current) + 1;
        scrollCache.put(app, next);
        return next;
    }

    private int countFor(String app) {
        Integer current = scrollCache.get(app);
        return current == null ? 0 : current;
    }

    // ---------------------------------------------------------------------- HUD

    private void createHud() {
        if (hudView != null || windowManager == null) {
            return;
        }

        try {
            hudView = LayoutInflater.from(this).inflate(R.layout.floating_hud, null);
            counterText = hudView.findViewById(R.id.scroll_counter_text);

            // The window stays attached while a monitored app is open (that part is what made
            // the HUD reliable), but the view itself is invisible until a reel is counted.
            hudView.setAlpha(0f);

            if (hudTypeface == null) {
                try {
                    hudTypeface = Typeface.createFromAsset(getAssets(), HUD_FONT_ASSET);
                } catch (Exception e) {
                    Log.e(TAG, "HUD font load failed, using default bold", e);
                    hudTypeface = Typeface.DEFAULT_BOLD;
                }
            }
            counterText.setTypeface(hudTypeface);

            layoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            // Position relative to the FULL screen (not the area between the
                            // status and navigation bars) so the centre is the true centre.
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            layoutParams.gravity = Gravity.CENTER;
            layoutParams.x = 0;
            layoutParams.y = 0;

            Log.i(TAG, "HUD INITIALIZED");

        } catch (Exception e) {
            Log.e(TAG, "HUD INIT FAILED", e);
            hudView = null;
            counterText = null;
            layoutParams = null;
        }
    }

    /**
     * Attach the overlay window if it is not attached, then push the current
     * count into the TextView. Attaching/detaching the window (instead of
     * toggling View visibility) is what makes "appears / disappears" reliable.
     */
    private void showHud(String app) {
        if (windowManager == null) {
            Log.e(TAG, "HUD SHOW FAILED | windowManager is null");
            return;
        }

        createHud();

        if (hudView == null || layoutParams == null) {
            Log.e(TAG, "HUD SHOW FAILED | hudView is null");
            return;
        }

        if (!isAttached) {
            try {
                // Always start a fresh attach invisible; only a counted reel makes it show.
                hudView.animate().cancel();
                hudView.setAlpha(0f);
                windowManager.addView(hudView, layoutParams);
                isAttached = true;
                Log.i(TAG, "HUD ATTACHED");
            } catch (Exception e) {
                Log.e(TAG, "HUD ATTACH FAILED", e);
                // Drop the view so the next attempt re-inflates a clean one
                // instead of getting permanently stuck.
                try {
                    windowManager.removeViewImmediate(hudView);
                } catch (Exception ignored) {
                }
                hudView = null;
                counterText = null;
                layoutParams = null;
                isAttached = false;
                return;
            }
        }

        updateHud(app);
        Log.i(TAG, "HUD SHOWN | " + app);
    }

    private void hideHud() {
        handler.removeCallbacks(fadeOutRunnable);
        if (hudView != null) {
            hudView.animate().cancel();
        }
        if (isAttached && windowManager != null && hudView != null) {
            try {
                windowManager.removeViewImmediate(hudView);
                Log.i(TAG, "HUD HIDDEN");
            } catch (Exception e) {
                Log.w(TAG, "HUD remove failed", e);
            }
        }
        // Cleared unconditionally so the HUD can never get stuck "attached".
        isAttached = false;
    }

    private void updateHud(String app) {
        if (!isAttached || counterText == null) {
            return;
        }
        int count = countFor(app);
        String label = String.valueOf(count);
        counterText.setText(label);
        // Shrink as digits grow so 3- and 4-digit counts still fit across the screen.
        counterText.setTextSize(label.length() <= 2 ? 180f : label.length() == 3 ? 130f : 95f);
        Log.i(TAG, "HUD UPDATED | " + app + " = " + count);
    }

    /**
     * Fade the count in, hold it, then fade it out. Called once per counted reel. If another
     * reel is counted while it is still showing, the number just updates and the 2 second
     * hold restarts, so fast scrolling keeps it on screen instead of flickering.
     */
    private void flashHud() {
        if (!isAttached || hudView == null) {
            return;
        }
        handler.removeCallbacks(fadeOutRunnable);
        hudView.animate().cancel();
        hudView.animate().alpha(1f).setDuration(HUD_FADE_IN_MS).start();
        handler.postDelayed(fadeOutRunnable, HUD_FADE_IN_MS + HUD_VISIBLE_MS);
    }

    // -------------------------------------------------------------- persistence

    private void schedulePersist() {
        if (persistScheduled) {
            return;
        }
        persistScheduled = true;
        handler.postDelayed(persistRunnable, PERSIST_DELAY_MS);
    }

    /**
     * Cheap: builds a 4-key JSON object and calls apply(), which writes off the
     * main thread. Always leaves a trailing write pending, so the last scroll of
     * a burst is never lost.
     */
    private void persistNow() {
        handler.removeCallbacks(persistRunnable);
        persistScheduled = false;

        if (prefs == null || cachedDate == null) {
            return;
        }

        prefs.edit()
                .putString(KEY_CURRENT_DATE, cachedDate)
                .putString(KEY_SCROLL_DATA, cacheToJson())
                .apply();

        Log.i(TAG, "PERSISTED | " + cachedDate + " | " + scrollCache);
    }

    private String cacheToJson() {
        JSONObject json = new JSONObject();
        try {
            for (String app : MONITORED_APPS) {
                json.put(app, countFor(app));
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON build failed", e);
        }
        return json.toString();
    }

    private void loadCounts(String jsonData) {
        zeroCache();
        try {
            JSONObject json = new JSONObject(jsonData);
            for (String app : MONITORED_APPS) {
                scrollCache.put(app, json.optInt(app, 0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache load failed, starting from zero", e);
            zeroCache();
        }
    }

    private void zeroCache() {
        for (String app : MONITORED_APPS) {
            scrollCache.put(app, 0);
        }
    }

    // --------------------------------------------------------------- daily reset

    /**
     * Establishes today's state BEFORE any scroll can arrive. The old code did
     * the date check lazily inside the save path, which meant the first scrolls
     * after startup could be written into yesterday's bucket and then wiped.
     */
    private void loadState() {
        String today = today();
        String savedDate = prefs.getString(KEY_CURRENT_DATE, null);
        String savedData = prefs.getString(KEY_SCROLL_DATA, null);

        cachedDate = today;
        lastDateCheckAt = SystemClock.elapsedRealtime();

        if (today.equals(savedDate) && savedData != null) {
            loadCounts(savedData);
            Log.i(TAG, "STATE LOADED | " + today + " | " + scrollCache);
            return;
        }

        if (savedDate != null && savedData != null) {
            Log.i(TAG, "DAILY RESET | archiving " + savedDate);
            archive(savedDate, savedData);
        }

        zeroCache();
        persistNow();
        Log.i(TAG, "STATE INITIALISED | " + today);
    }

    /** Called before each increment, but only recomputes the date once a minute. */
    private void maybeRollOverDate() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastDateCheckAt < DATE_CHECK_INTERVAL_MS) {
            return;
        }
        lastDateCheckAt = now;

        String today = today();
        if (today.equals(cachedDate)) {
            return;
        }

        Log.i(TAG, "DAILY RESET | " + cachedDate + " -> " + today);

        archive(cachedDate, cacheToJson());
        zeroCache();
        cachedDate = today;
        persistNow();
    }

    private void archive(final String date, final String jsonData) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ExternalDatabaseHandler.saveData(date, jsonData);
                } catch (Exception e) {
                    Log.e(TAG, "DB archive failed for " + date, e);
                }
            }
        }, "nudge-archive").start();
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
