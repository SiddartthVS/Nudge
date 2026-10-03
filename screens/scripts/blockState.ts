/**
 * Bridges to the native BlockModule, which reads and writes BlockState - see
 * android/app/src/main/java/com/nudge/BlockState.java. When an app is
 * "blocked", TrackerService performs a back gesture the instant it counts a
 * reel from that app, instead of just counting it.
 *
 * Used only by Blockbutton.tsx; nothing else needs to know an app is blocked.
 */

import { NativeModules } from 'react-native';

const { BlockModule } = NativeModules;

/** Reads whether `pkg` is currently blocked. Defaults to false if the native module isn't available. */
export async function isAppBlocked(pkg: string): Promise<boolean> {
  if (!BlockModule?.isBlocked) {
    return false;
  }
  try {
    return await BlockModule.isBlocked(pkg);
  } catch {
    return false;
  }
}

/** Fire-and-forget: tells the native side to start/stop blocking `pkg`, effective immediately. */
export function setAppBlocked(pkg: string, blocked: boolean): void {
  BlockModule?.setBlocked?.(pkg, blocked);
}
