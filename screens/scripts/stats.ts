/**
 * Bridges to the native StatsModule.getWeekHistory() call, which returns the
 * on-device scroll history that TrackerService/WeekHistoryStore persist
 * natively - see android/app/src/main/java/com/nudge/WeekHistoryStore.java.
 *
 * This is a *local, on-device* history (today plus a short recent-days
 * cache), separate from the Supabase-backed range totals in rangeStats.ts.
 * WeekStats uses this file (via useWeekHistory.ts) because a 7-day chart
 * only ever needs recent, always-on-device data. Anything that needs a
 * longer or user-selectable range (CountDisplay's range pills, AppStats'
 * dropdown) uses rangeStats.ts/rangeCache.ts instead, which also reaches
 * out to Supabase for older days.
 */

import { NativeModules } from 'react-native';

/** Per-app scroll counts for a single day, e.g. { "com.instagram.android": 214 }. */
export type DayCounts = Record<string, number>;

/** A day's counts keyed by date, e.g. { "2026-09-19": { ... } }. */
export type WeekHistory = Record<string, DayCounts>;

const { StatsModule } = NativeModules;

/**
 * Reads the on-device scroll history that TrackerService/WeekHistoryStore
 * persist natively.
 *
 * Resolves to {} rather than throwing whenever there is nothing sensible to
 * show yet - the native module missing (iOS, or Android before the JS
 * bridge is up), no data recorded yet, or a read failure. Screens can then
 * just render zeros instead of handling an error state.
 */
export async function getWeekHistory(): Promise<WeekHistory> {
  if (!StatsModule?.getWeekHistory) {
    return {};
  }
  try {
    const raw: string = await StatsModule.getWeekHistory();
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

/**
 * "yyyy-MM-dd" for a given date, in the device's local timezone. Shared so
 * every screen that keys into WeekHistory (WeekStats, useWeekHistory, ...)
 * formats dates the same way as the native side does.
 */
export function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
