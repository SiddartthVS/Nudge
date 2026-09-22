/**
 * Reads the three Android permissions NUDGE needs to work: drawing the HUD
 * overlay, the accessibility service that does the actual scroll counting,
 * and being excluded from battery optimisation so the service isn't killed
 * in the background. All three are checked via PermissionsModule.java
 * (android/app/src/main/java/com/nudge/PermissionsModule.java).
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

export type PermissionStatus = {
  hasOverlay: boolean;
  hasAccess: boolean;
  hasBattery: boolean;
};

/** Reads the current state of all three permissions in parallel. */
export async function checkAllPermissions(): Promise<PermissionStatus> {
  const [hasOverlay, hasAccess, hasBattery] = await Promise.all([
    pm.checkOverlayPermission(),
    pm.checkAccessibilityPermission(),
    pm.checkBatteryPermission(),
  ]);

  return { hasOverlay, hasAccess, hasBattery };
}

/** True only once every one of the three permissions has been granted. */
export function isFullyGranted(status: PermissionStatus): boolean {
  return status.hasOverlay && status.hasAccess && status.hasBattery;
}
