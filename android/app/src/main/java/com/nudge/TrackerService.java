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
 * NUDGE - the accessibility service that watches the screen, counts reels, and drives the
 * floating HUD. Runs natively and independently of React Native/JS. See Overview.md, section 1,
 * for the full story of why a raw scroll event isn't enough on its own (ReelSignal handles
 * that part) and how the HUD stays reliable across app switches.
 *
 * Data flow: candidate event -> ReelSignal decides if the reel changed -> increment in memory
 * -> update HUD -> schedule a persist. The in-memory increment and HUD update are immediate;
 * only the SharedPreferences write is delayed (trailing debounce).
 */
public class TrackerService extends AccessibilityService {

    private static final String TAG = "NudgeTracker";

    static final String PREFS_NAME = "NudgePrefs";
    static final String KEY_SCROLL_DATA = "scroll_data";
    static final String KEY_CURRENT_DATE = "current_date";

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

    /**
     * Floor between reel-content checks for the same app. Real reel transitions never happen
     * faster than this, so it only bounds how often node-tree work runs during a fast fling -
     * it never delays a genuine count, since two distinct reels are always further apart.
     */
    private static final long REEL_CHECK_DEBOUNCE_MS = 120L;
    private final Map<String, Long> lastReelCheckAt = new HashMap<>();

    /**
     * Trailing "settle" check. The throttle above can skip the LAST event of a swipe - the one
     * that fires once the new reel is actually on screen. Without a follow-up read that reel
     * would never be seen. This re-reads the screen once, this long after the last event.
     */
    private static final long REEL_SETTLE_DELAY_MS = 250L;
    private final Map<String, Runnable> settleRunnables = new HashMap<>();

    /** The only place the four monitored package names are declared. */
    static final Set<String> MONITORED_APPS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "com.instagram.android",
                    "com.google.android.youtube",
                    "com.facebook.katana",
                    "com.snapchat.android")));

    /** Today's counts, per app. What gets saved to disk and shown on the HUD. */
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

        // In case an earlier day failed to upload (e.g. phone was offline at midnight).
        SupabaseSync.retryPending(this);

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
     * Runs synchronously, not posted to the Handler - posting it meant super.onDestroy() had
     * already run by the time this fired, and the overlay window leaked.
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
     * A scroll/content-change event from a monitored app MIGHT mean the user moved to a new
     * reel - handed to ReelSignal to decide for certain by reading the actual screen content.
     */
    private void handleReelCandidateEvent(String app, AccessibilityEvent event) {
        // A real event from a monitored app is proof that app is on screen right now, stronger
        // evidence than Android's own window-changed signal (which can be unreliable behind
        // something like a fingerprint lock screen). See isTransientPackage for more on that.
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

    /** Reads the current screen and counts a reel if ReelSignal says it's a new one. */
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
     * Brings the HUD in line with reality: if we're seeing events from a monitored app, that
     * app is on screen, full stop - regardless of what the last window-state event said (or
     * failed to say). Cheap, so safe to call on every event, not just counted ones.
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
        if (app.equals(getPackageName())) {
            // Nudge itself was brought to the front - flush now instead of waiting for the
            // debounce, so numbers shown in the app are never stale.
            persistNow();
        }

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
     * System UI, keyboards, lock/biometric prompts and Nudge itself push windows on top of the
     * app the user is actually looking at - treating those as the foreground app would make
     * the HUD disappear mid-scroll. This match is intentionally loose (keywords, not exact
     * package names) since app-lock gates vary by OEM; even if one slips through, the moment
     * the gated app itself produces a real event, confirmMonitoredForeground corrects the HUD
     * anyway. So this filter mainly avoids visible flicker, it isn't load-bearing for accuracy.
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

            // The window stays attached the whole time a monitored app is open (that's what
            // makes the HUD reliable) - the view itself starts invisible until a reel counts.
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
                            // Position relative to the FULL screen, not just the area between
                            // the status and navigation bars, so centre gravity is the true centre.
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
     * Attaches the overlay window if it isn't attached, then pushes the current count into the
     * TextView. Attaching/detaching the whole window (instead of toggling View visibility) is
     * what makes "appears / disappears" reliable across devices.
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
                // Drop the view so the next attempt re-inflates a clean one instead of getting
                // permanently stuck.
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
     * Fades the count in, holds it, then fades it out. Called once per counted reel. If another
     * reel is counted while it's still showing, the number just updates and the hold restarts -
     * so fast scrolling keeps the HUD on screen instead of flickering it on and off.
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
     * Writes today's counts to disk. Cheap - apply() writes off the main thread - and always
     * leaves a trailing write pending, so the last scroll of a burst is never lost.
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
     * Establishes today's state BEFORE any scroll can arrive. Doing this check lazily inside
     * the save path (the old approach) meant the first scrolls after startup could land in
     * yesterday's bucket and then get wiped.
     */
    private void loadState() {
        String today = today();
        String savedDate = prefs.getString(KEY_CURRENT_DATE, null);
        String savedData = prefs.getString(KEY_SCROLL_DATA, null);

        cachedDate = today;

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

    /** Checked on every counted reel; rolls the counters over the instant the date changes. */
    private void maybeRollOverDate() {
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

    /**
     * Hands a finished day to local history (what the "This week" chart reads) and to
     * SupabaseSync, which queues it on disk and uploads on its own background thread - so this
     * returns immediately and a failed upload is retried later.
     */
    private void archive(String date, String jsonData) {
        WeekHistoryStore.recordDay(getApplicationContext(), date, jsonData);
        SupabaseSync.archiveDay(getApplicationContext(), date, jsonData);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
