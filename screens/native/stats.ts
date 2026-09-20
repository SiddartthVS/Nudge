import { NativeModules } from 'react-native';

/** Per-app scroll counts for a single day, e.g. { "com.instagram.android": 214 }. */
export type DayCounts = Record<string, number>;

/** A day's counts keyed by date, e.g. { "2026-09-19": { ... } }. */
export type WeekHistory = Record<string, DayCounts>;

const { StatsModule } = NativeModules;

/**
 * Reads the on-device scroll history that TrackerService/WeekHistoryStore persist natively
 * (see android/app/src/main/java/com/nudge/WeekHistoryStore.java).
 *
 * Resolves to {} rather than throwing whenever there is nothing sensible to show yet - the
 * native module missing (iOS, or Android before the JS bridge is up), no data recorded yet,
 * or a read failure. Screens can then just render zeros instead of handling an error state.
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
 * Today's per-app counts, e.g. { "com.instagram.android": 214 }. Just today's slice of
 * getWeekHistory() - WeekHistoryStore already merges today's live counters into that result,
 * so no separate native call is needed for this.
 */
export async function getTodayCounts(): Promise<DayCounts> {
  const history = await getWeekHistory();
  return history[formatDate(new Date())] ?? {};
}

/**
 * "yyyy-MM-dd" for a given date, in the device's local timezone. Shared so every screen that
 * keys into WeekHistory (WeekStats, CountDisplay, ...) formats dates the same way.
 */
export function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
