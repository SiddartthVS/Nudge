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

public class PermissionsModule extends ReactContextBaseJavaModule {
    PermissionsModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "PermissionsModule";
    }

    @ReactMethod
    public void checkOverlayPermission(Promise promise) {
        boolean hasOverlay = Settings.canDrawOverlays(getReactApplicationContext());
        promise.resolve(hasOverlay);
    }

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

    @ReactMethod
    public void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getReactApplicationContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getReactApplicationContext().startActivity(intent);
    }

    @ReactMethod
    public void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getReactApplicationContext().startActivity(intent);
    }

    /**
     * True when Android's battery optimisation is turned off for Nudge (on Xiaomi/MIUI/HyperOS
     * this matches Battery saver -> "No restrictions"). Without it these phones freeze the
     * accessibility service in the background, so reels stop being counted until Nudge is opened.
     */
    @ReactMethod
    public void checkBatteryPermission(Promise promise) {
        Context context = getReactApplicationContext();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean unrestricted = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        promise.resolve(unrestricted);
    }

    /**
     * Tries the most direct screen first and falls back, because OEM builds do not all support
     * every intent: 1) the one-tap "allow always running in background" dialog, 2) the battery
     * optimisation list, 3) Nudge's own app-info page.
     */
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