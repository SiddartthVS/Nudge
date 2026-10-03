package com.nudge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import java.util.Locale;

/**
 * Lets the React Native permissions screen check and request what Nudge needs to keep working:
 * draw-over-other-apps (for the HUD), the accessibility service (for tracking), battery
 * optimisation exemption, and - on phones that have one - the maker's own "autostart /
 * background" switch (Xiaomi, Samsung, Oppo, Vivo, Huawei).
 *
 * Every settings screen is opened with startFirstAvailable(), which never throws: if a phone
 * doesn't have a screen, it just tries the next one.
 */
public class PermissionsModule extends ReactContextBaseJavaModule {

    private static final String TAG = "NudgePermissions";

    // Separate prefs file from the scroll data, since this is setup state, not counts.
    private static final String SETUP_PREFS = "NudgeSetup";
    private static final String KEY_BACKGROUND_DONE = "background_step_done";

    PermissionsModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "PermissionsModule";
    }

    // ------------------------------------------------------------------ helpers

    /** Opens the first of these screens that this phone accepts. Returns false if none worked. */
    private boolean startFirstAvailable(Intent... intents) {
        Context context = getReactApplicationContext();
        for (Intent intent : intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return true;
            } catch (Exception e) {
                // Missing screen (ActivityNotFoundException) or a screen the maker doesn't
                // let other apps open (SecurityException) - try the next option.
                Log.w(TAG, "Could not open " + intent + ", trying next", e);
            }
        }
        return false;
    }

    /** Android's standard "App info" page for Nudge. Exists on every phone. */
    private Intent appDetailsIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getReactApplicationContext().getPackageName()));
    }

    // ------------------------------------------------------------------ overlay

    /** True if Nudge is allowed to draw the floating HUD over other apps. */
    @ReactMethod
    public void checkOverlayPermission(Promise promise) {
        boolean hasOverlay = Settings.canDrawOverlays(getReactApplicationContext());
        promise.resolve(hasOverlay);
    }

    /** Opens the screen where the user grants the overlay permission. */
    @ReactMethod
    public void requestOverlayPermission() {
        startFirstAvailable(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getReactApplicationContext().getPackageName())),
                appDetailsIntent());
    }

    // ------------------------------------------------------------ accessibility

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

    /** Opens Android's Accessibility settings so the user can turn Nudge's service on. */
    @ReactMethod
    public void requestAccessibilityPermission() {
        startFirstAvailable(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS));
    }

    /**
     * Opens Nudge's App info page. Used for the "Allow restricted settings" hint: on Android 13+
     * an APK installed outside a store has its accessibility toggle locked until the user
     * allows it from that page's menu.
     */
    @ReactMethod
    public void openAppInfo() {
        startFirstAvailable(appDetailsIntent(), new Intent(Settings.ACTION_SETTINGS));
    }

    // ------------------------------------------------------------------ battery

    /**
     * True when battery optimisation is off for Nudge. Without this, some phones freeze the
     * accessibility service in the background and counting stops until Nudge is reopened.
     * Note: this only reads Android's own setting - makers add separate switches on top of it,
     * which is what the background step below is for.
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
        Uri packageUri = Uri.parse("package:" + getReactApplicationContext().getPackageName());

        startFirstAvailable(
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetailsIntent());
    }

    // ------------------------------------------- maker-specific background switch

    /**
     * Which maker's extra background switch (if any) this phone has. Android itself has no
     * "autostart" - each maker invented its own - so this is decided from the phone's maker
     * and brand (POCO and Redmi report Xiaomi; iQOO reports vivo; and so on).
     *
     * Returns "xiaomi", "samsung", "oppo" (also OnePlus, realme), "vivo" (also iQOO),
     * "huawei" (also Honor), or "none" for everything else (Pixel, Motorola, Nothing, ...).
     */
    private static String backgroundFamily() {
        String maker = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);

        if (maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")) {
            return "xiaomi";
        }
        if (maker.contains("samsung")) {
            return "samsung";
        }
        if (maker.contains("oppo") || maker.contains("oneplus") || maker.contains("realme")) {
            return "oppo";
        }
        if (maker.contains("vivo") || maker.contains("iqoo")) {
            return "vivo";
        }
        if (maker.contains("huawei") || maker.contains("honor")) {
            return "huawei";
        }
        return "none";
    }

    private SharedPreferences setupPrefs() {
        return getReactApplicationContext().getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE);
    }

    /** See backgroundFamily(). */
    @ReactMethod
    public void getBackgroundFamily(Promise promise) {
        promise.resolve(backgroundFamily());
    }

    /**
     * True when there's nothing to do (this phone has no such switch) or the user has already
     * been taken to the right screen. These switches can't be read reliably from an app - the
     * only known way uses hidden APIs and was only tested up to MIUI 14 - so "opened it once"
     * is the best signal available.
     */
    @ReactMethod
    public void checkBackgroundPermission(Promise promise) {
        boolean done = backgroundFamily().equals("none")
                || setupPrefs().getBoolean(KEY_BACKGROUND_DONE, false);
        promise.resolve(done);
    }

    /**
     * Opens the best screen for this phone's background switch and marks the step as done.
     * Xiaomi gets its Autostart list directly; every other maker (and Xiaomi, if that screen
     * isn't available on this version) gets the standard App info page, where the battery /
     * background options live. Resolves with what was opened: "autostart_list", "app_info"
     * or "none" - handy to check in logcat (tag NudgePermissions) on a new phone.
     */
    @ReactMethod
    public void openBackgroundSettings(Promise promise) {
        String family = backgroundFamily();
        String opened = "none";

        if (family.equals("xiaomi")) {
            Intent autostart = new Intent().setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            if (startFirstAvailable(autostart)) {
                opened = "autostart_list";
            }
        }

        if (opened.equals("none") && startFirstAvailable(appDetailsIntent())) {
            opened = "app_info";
        }

        if (!opened.equals("none")) {
            setupPrefs().edit().putBoolean(KEY_BACKGROUND_DONE, true).apply();
        }

        Log.i(TAG, "BACKGROUND SETTINGS | family=" + family + " | opened=" + opened
                + " | " + Build.MANUFACTURER + "/" + Build.BRAND + "/" + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE);
        promise.resolve(opened);
    }

}
