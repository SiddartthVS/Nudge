/**
 * Bridges to the native StatsModule.getRangeTotals(days) call, which asks
 * SupabaseReader.java for the combined per-app totals over a date range -
 * see android/app/src/main/java/com/nudge/SupabaseReader.java and
 * StatsModule.java for how that number is actually assembled on the Android
 * side (today's live counters, anything still queued for upload, plus
 * whatever has already synced to Supabase).
 *
 * This file only defines the raw network call and its data shape. Screens
 * should not call getRangeTotals() directly - use the cached, deduplicated
 * version in rangeCache.ts instead, which wraps this function with staleness
 * tracking so switching between range pills feels instant.
 */

import { NativeModules } from 'react-native';
import { DayCounts } from './stats';

const { StatsModule } = NativeModules;

/**
 * The date ranges the UI lets the user pick between (CountDisplay's range
 * pills, AppStats' dropdown), expressed as "how many days back from today,
 * inclusive". `lifetime` is 0, which StatsModule.java treats as "no lower
 * bound" rather than an actual day count.
 */
export const RANGE_DAYS = {
  today: 1,
  week: 7,
  month: 30,
  year: 365,
  lifetime: 0,
} as const;

export type RangeTotals = {
  /** Per-app totals for the requested range, e.g. { "com.instagram.android": 812 }. */
  totals: DayCounts;
  /**
   * False if the range fell back to on-device data only (e.g. Supabase was
   * unreachable), meaning the totals may be missing older days that only
   * exist in the cloud. True once a full, successful fetch has completed.
   */
  complete: boolean;
};

/**
 * Fetches per-app totals for the last `days` days (or the whole lifetime,
 * for `RANGE_DAYS.lifetime`). Never throws - on any failure, or if the
 * native module isn't available yet, it resolves to an empty, incomplete
 * result so callers can render zeros instead of handling an error state.
 */
export async function getRangeTotals(days: number): Promise<RangeTotals> {
  if (!StatsModule?.getRangeTotals) {
    return { totals: {}, complete: false };
  }
  try {
    const raw: string = await StatsModule.getRangeTotals(days);
    return JSON.parse(raw);
  } catch {
    return { totals: {}, complete: false };
  }
}
