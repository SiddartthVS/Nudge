package com.nudge;

import android.content.Intent;
import android.net.Uri;
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

}