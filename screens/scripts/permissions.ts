import { NativeModules } from 'react-native';

const { PermissionsModule: pm } = NativeModules;

export type PermissionStatus = {
  hasOverlay: boolean;
  hasAccess: boolean;
  hasBattery: boolean;
};

export async function checkAllPermissions(): Promise<PermissionStatus> {
  const [hasOverlay, hasAccess, hasBattery] = await Promise.all([
    pm.checkOverlayPermission(),
    pm.checkAccessibilityPermission(),
    pm.checkBatteryPermission(),
  ]);

  return { hasOverlay, hasAccess, hasBattery };
}

export function isFullyGranted(status: PermissionStatus): boolean {
  return status.hasOverlay && status.hasAccess && status.hasBattery;
}
