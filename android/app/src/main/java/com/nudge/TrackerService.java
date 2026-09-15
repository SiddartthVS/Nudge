package com.nudge;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TrackerService extends AccessibilityService {

    private static final String TAG = "NudgeTrackerService";

    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String GBOARD = "com.google.android.inputmethod.latin";

    /*
     * Apps that should trigger the Nudge HUD.
     */
    private final Set<String> targetApps = new HashSet<>(Arrays.asList(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.twitter.android"
    ));

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams windowParams;

    /*
     * This is the actual source of truth for whether the View
     * has been successfully added to WindowManager.
     */
    private boolean isAttached = false;

    /*
     * Last package reported by AccessibilityService.
     * Used to avoid doing unnecessary work for duplicate events.
     */
    private String currentForegroundPackage = null;

    /*
     * Used to prevent stale delayed callbacks from changing
     * the overlay after the user has already switched apps.
     */
    private int foregroundGeneration = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        /*
         * Everything touching WindowManager/View happens on
         * the main thread.
         */
        mainHandler.post(() -> {

            windowManager =
                    (WindowManager) getSystemService(WINDOW_SERVICE);

            /*
             * Prepare the overlay.
             *
             * We DON'T necessarily show it here because we don't
             * yet know which app is in the foreground.
             */
            ensureOverlayCreated();

            /*
             * Start with the HUD hidden.
             */
            hideOverlay();

        });
    }

    /**
     * Creates the overlay object if it doesn't exist.
     *
     * This method does NOT assume that the View is currently
     * attached to WindowManager.
     */
    private void ensureOverlayCreated() {

        if (windowManager == null) {
            return;
        }

        if (floatingView != null) {
            return;
        }

        floatingView = LayoutInflater
                .from(this)
                .inflate(R.layout.floating_hud, null);

        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT
        );

        windowParams.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        windowParams.y = 100;

        /*
         * Never let the newly-created View appear until
         * we explicitly decide that the foreground app
         * is a target app.
         */
        floatingView.setVisibility(View.GONE);
    }

    /**
     * Makes sure the View is actually registered with
     * WindowManager.
     *
     * This is the important recovery mechanism.
     */
    private boolean ensureOverlayAttached() {

        if (windowManager == null) {
            return false;
        }

        ensureOverlayCreated();

        if (floatingView == null) {
            return false;
        }

        if (isAttached) {
            return true;
        }

        try {

            windowManager.addView(
                    floatingView,
                    windowParams
            );

            isAttached = true;

            return true;

        } catch (WindowManager.BadTokenException e) {

            /*
             * Android rejected the window token.
             *
             * Don't crash the AccessibilityService.
             */
            isAttached = false;
            return false;

        } catch (IllegalStateException e) {

            /*
             * Can happen if the View was already added/removed
             * while Android was changing window state.
             */
            isAttached = false;
            return false;

        } catch (Exception e) {

            /*
             * Last line of defense.
             */
            isAttached = false;
            return false;
        }
    }

    /**
     * Show the HUD.
     */
    private void showOverlay() {

        if (!ensureOverlayAttached()) {
            return;
        }

        if (floatingView != null) {
            floatingView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the HUD.
     *
     * We intentionally DO NOT remove the View from WindowManager.
     *
     * GONE is cheap and avoids repeatedly destroying/recreating
     * the WindowManager window.
     */
    private void hideOverlay() {

        if (floatingView != null) {
            floatingView.setVisibility(View.GONE);
        }
    }

    /**
     * Completely remove the overlay.
     *
     * Used only when the service itself is being destroyed.
     */
    private void removeOverlay() {

        if (floatingView == null) {
            return;
        }

        if (windowManager == null) {
            floatingView = null;
            isAttached = false;
            return;
        }

        if (!isAttached) {
            floatingView = null;
            return;
        }

        try {

            windowManager.removeViewImmediate(
                    floatingView
            );

        } catch (IllegalArgumentException ignored) {

            /*
             * View wasn't attached anymore.
             */

        } catch (Exception ignored) {
            /*
             * Never allow cleanup to crash the service.
             */
        }

        isAttached = false;
        floatingView = null;
    }

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event
    ) {

        int eventType = event.getEventType();

        /*
         * We mainly care about window changes.
         */
        if (eventType !=
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                &&
                eventType !=
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            return;
        }

        CharSequence packageName =
                event.getPackageName();

        if (packageName == null) {
            return;
        }

        String packageString =
                packageName.toString();

        /*
         * Ignore system windows that should not change
         * our foreground-app state.
         */
        if (packageString.equals(SYSTEM_UI)
                || packageString.equals(GBOARD)) {

            return;
        }

        /*
         * Ignore our own application.
         */
        if (packageString.equals(getPackageName())) {
            return;
        }

        /*
         * Don't process the same package repeatedly.
         */
        if (packageString.equals(currentForegroundPackage)) {
            return;
        }

        /*
         * New foreground package.
         */
        currentForegroundPackage = packageString;

        /*
         * Increment generation.
         *
         * Any previously scheduled operation becomes stale.
         */
        foregroundGeneration++;

        final int generation =
                foregroundGeneration;

        final String foregroundPackage =
                packageString;

        /*
         * Small debounce.
         *
         * Android can generate several window events while
         * transitioning between applications.
         */
        mainHandler.postDelayed(() -> {

            /*
             * Ignore this callback if the user has already
             * switched to another package.
             */
            if (generation != foregroundGeneration) {
                return;
            }

            /*
             * Make sure the service is still alive.
             */
            if (windowManager == null) {
                return;
            }

            if (targetApps.contains(foregroundPackage)) {

                /*
                 * Target app.
                 *
                 * Ensure the window exists and is attached,
                 * then show it.
                 */
                showOverlay();

            } else {

                /*
                 * Non-target app.
                 */
                hideOverlay();
            }

        }, 50);
    }

    @Override
    public void onInterrupt() {

        /*
         * AccessibilityService has been interrupted.
         *
         * Don't destroy the View here. Just hide it.
         */
        mainHandler.post(() -> {
            hideOverlay();
        });
    }

    @Override
    public void onDestroy() {

        /*
         * Cancel pending accessibility callbacks.
         */
        mainHandler.removeCallbacksAndMessages(null);

        /*
         * Remove the actual WindowManager window.
         */
        mainHandler.post(() -> {

            removeOverlay();

            windowManager = null;
            currentForegroundPackage = null;
        });

        super.onDestroy();
    }
}