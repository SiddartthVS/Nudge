/**
 * Reads what NUDGE needs to keep working: drawing the HUD overlay, the accessibility service
 * that does the actual scroll counting, being excluded from battery optimisation, and - on
 * phones that have one - the maker's own autostart/background switch. All of it is checked via
 * PermissionsModule.java (android/app/src/main/java/com/nudge/PermissionsModule.java).
 *
 * Used in two places for two different reasons:
 *   - App.tsx calls this once on launch to decide whether to show the
 *     permissions screen at all, or skip straight to the home screen.
 *   - PermissionsScreen.tsx calls this repeatedly (on mount and whenever the
 *     app returns to the foreground) to keep its progress tracker and cards
 *     live as the user grants permissions in Android's Settings app.
 */

import { NativeModules } from 'react-native';

const { PermissionsModule: pm } = NativeModules;

/**
 * Android has no built-in "autostart"; each maker added its own switch (or none). The family is
 * decided natively from the phone's maker/brand. 'none' = nothing extra to do (Pixel, Motorola,
 * Nothing, ...).
 */
export type BackgroundFamily = 'none' | 'xiaomi' | 'samsung' | 'oppo' | 'vivo' | 'huawei';

export type PermissionStatus = {
  hasOverlay: boolean;
  hasAccess: boolean;
  hasBattery: boolean;
  backgroundFamily: BackgroundFamily;
  /** Always true when backgroundFamily is 'none'. Otherwise true once the user was sent to the screen. */
  hasBackground: boolean;
};

/** Reads the current state of every step in parallel. */
export async function checkAllPermissions(): Promise<PermissionStatus> {
  const [hasOverlay, hasAccess, hasBattery, backgroundFamily, hasBackground] = await Promise.all([
    pm.checkOverlayPermission(),
    pm.checkAccessibilityPermission(),
    pm.checkBatteryPermission(),
    pm.getBackgroundFamily(),
    pm.checkBackgroundPermission(),
  ]);

  return { hasOverlay, hasAccess, hasBattery, backgroundFamily, hasBackground };
}

/** True only once every step that applies to this phone has been done. */
export function isFullyGranted(status: PermissionStatus): boolean {
  return status.hasOverlay && status.hasAccess && status.hasBattery && status.hasBackground;
}

/**
 * Card text for the extra step, per maker. Kept to one short line each - the card is small.
 *
 * Confidence in each path (from documentation, not tested on every device):
 *   xiaomi  - high: opens the Autostart list directly; confirmed to be the fix on a POCO X7 / HyperOS 3.
 *   samsung - medium: Samsung has no autostart; the equivalent is the per-app battery setting
 *             (and, optionally, Settings > Battery > Background usage limits > Never sleeping apps).
 *   oppo    - medium: ColorOS / OxygenOS / realme UI call it "auto-launch" / "background activity".
 *   vivo    - low: wording and location vary between Funtouch OS and OriginOS.
 *   huawei  - low: EMUI / HarmonyOS / MagicOS "App launch" > "Manage manually".
 * Every maker except Xiaomi opens the standard App info page, which exists on all phones.
 */
export const BACKGROUND_STEPS: Record<Exclude<BackgroundFamily, 'none'>, { title: string; description: string }> = {
  xiaomi: { title: 'Autostart', description: 'Turn on Autostart for Nudge' },
  samsung: { title: 'Battery: Unrestricted', description: 'App info ➜ Battery ➜ Unrestricted' },
  oppo: { title: 'Allow auto-launch', description: 'App info ➜ Battery ➜ Auto-launch' },
  vivo: { title: 'Background start', description: 'Allow Nudge to run in background' },
  huawei: { title: 'App launch', description: 'Battery ➜ App launch ➜ Manage manually' },
};
