package com.nudge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

/**
 * Lets the React Native permissions screen check and request the three Android permissions
 * Nudge needs: draw-over-other-apps (for the HUD), the accessibility service (for tracking),
 * and battery-optimisation exemption (so the service isn't killed in the background).
 */
public class PermissionsModule extends ReactContextBaseJavaModule {
    PermissionsModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "PermissionsModule";
    }

    /** True if Nudge is allowed to draw the floating HUD over other apps. */
    @ReactMethod
    public void checkOverlayPermission(Promise promise) {
        boolean hasOverlay = Settings.canDrawOverlays(getReactApplicationContext());
        promise.resolve(hasOverlay);
    }

    /** True if TrackerService is turned on in Android's Accessibility settings. */
    @ReactMethod
    public void checkAccessibilityPermission(Promise promise) {
        boolean hasAccessibility = false;
        String enabledServices = Settings.Secure.getString(
                getReactApplicationContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices != null && enabledServices.contains(getReactApplicationContext().getPackageName())) {
            hasAccessibility = true;
        }

        promise.resolve(hasAccessibility);
    }

    /** Opens the system screen where the user grants the overlay permission. */
    @ReactMethod
    public void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getReactApplicationContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getReactApplicationContext().startActivity(intent);
    }

    /** Opens Android's Accessibility settings so the user can turn Nudge's service on. */
    @ReactMethod
    public void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getReactApplicationContext().startActivity(intent);
    }

    /**
     * True when battery optimisation is off for Nudge. Without this, some phones (Xiaomi/MIUI/
     * HyperOS especially) freeze the accessibility service in the background and counting stops
     * until Nudge is reopened.
     */
    @ReactMethod
    public void checkBatteryPermission(Promise promise) {
        Context context = getReactApplicationContext();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean unrestricted = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        promise.resolve(unrestricted);
    }

    /** Tries three screens in order, since not every phone supports the same one. */
    @ReactMethod
    public void requestBatteryPermission() {
        Context context = getReactApplicationContext();
        Uri packageUri = Uri.parse("package:" + context.getPackageName());

        Intent[] attempts = new Intent[] {
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        };

        for (Intent intent : attempts) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return;
            } catch (Exception ignored) {
                // Not supported on this phone - try the next one.
            }
        }
    }

}
