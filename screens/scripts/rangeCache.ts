/**
 * In-memory cache over rangeStats.getRangeTotals(), so switching between
 * range pills/dropdowns (Today, This week, ... Lifetime) feels instant
 * instead of triggering a fresh network round-trip every time.
 *
 * The cache is a plain module-level Map, not component state, which means:
 *   - It's shared by every component that reads a given range (CountDisplay
 *     and AppStats both benefit from a single Lifetime fetch, for example).
 *   - It lives for as long as the JS engine is alive and clears on a real
 *     app restart - there's no persistence to disk here.
 *
 * Each range has its own "staleness" window (STALE_MS below): a currently
 * selected range keeps refreshing itself on POLL_INTERVAL_MS elsewhere
 * (CountDisplay/AppStats own that timer), but this module makes sure that
 * only actually stale ranges trigger a real network request - Today refreshes
 * far more often than Lifetime, because Today changes on every scroll while
 * Lifetime barely moves minute to minute.
 */

import { useEffect, useState } from 'react';
import { getRangeTotals, RANGE_DAYS, RangeTotals } from './rangeStats';

/** Every range the UI can request, used by refreshAllRangeCaches(). */
const ALL_RANGES = [RANGE_DAYS.today, RANGE_DAYS.week, RANGE_DAYS.month, RANGE_DAYS.year, RANGE_DAYS.lifetime];

/**
 * How long a cached result for each range is considered fresh enough to
 * reuse without refetching. Cheap/volatile ranges (Today) get a short
 * window; expensive/stable ranges (Lifetime) get a long one, since fetching
 * a device's entire history is the priciest call and its total rarely
 * changes within a few minutes.
 */
const STALE_MS: Record<number, number> = {
  [RANGE_DAYS.today]: 5 * 1000,
  [RANGE_DAYS.week]: 30 * 1000,
  [RANGE_DAYS.month]: 2 * 60 * 1000,
  [RANGE_DAYS.year]: 5 * 60 * 1000,
  [RANGE_DAYS.lifetime]: 5 * 60 * 1000,
};

/** The last known-good result for each range, keyed by RANGE_DAYS value. */
const cache = new Map<number, RangeTotals>();
/** When each range was last fetched *successfully* (result.complete === true). */
const fetchedAt = new Map<number, number>();
/** In-flight requests, so two simultaneous callers share one network call. */
const inFlight = new Map<number, Promise<RangeTotals>>();
/** Components subscribed via useRangeTotals(), re-rendered on every cache update. */
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach(listener => listener());
}

/**
 * A range counts as stale if it has never been fetched, or if its last
 * *successful* fetch is older than its STALE_MS window. A failed/incomplete
 * fetch does not count as fresh, so the next refresh attempt will retry it.
 */
function isStale(days: number): boolean {
  const last = fetchedAt.get(days);
  if (last === undefined) {
    return true;
  }
  return Date.now() - last > (STALE_MS[days] ?? 60 * 1000);
}

/**
 * Fetches one range and updates the cache. Deduplicates concurrent calls for
 * the same range via `inFlight`, so rapid pill-tapping or overlapping
 * refresh triggers never fire more than one request per range at a time.
 */
async function load(days: number): Promise<RangeTotals> {
  const pending = inFlight.get(days);
  if (pending) {
    return pending;
  }

  const request = getRangeTotals(days)
    .then(result => {
      cache.set(days, result);
      if (result.complete) {
        fetchedAt.set(days, Date.now());
      }
      inFlight.delete(days);
      notify();
      return result;
    })
    .catch(() => {
      inFlight.delete(days);
      // Keep whatever was cached before rather than clearing it on failure,
      // so the UI keeps showing the last good number instead of flashing to zero.
      return cache.get(days) ?? { totals: {}, complete: false };
    });

  inFlight.set(days, request);
  return request;
}

/**
 * Refreshes one range in the background if it's stale (or always, when
 * `force` is true). Fire-and-forget: callers that need the result should use
 * useRangeTotals() instead, which re-renders once this resolves.
 */
export function refreshRangeCache(days: number, force = false) {
  if (force || isStale(days)) {
    load(days);
  }
}

/**
 * Refreshes every range at once. Used when the app opens or returns to the
 * foreground, so all five pills/dropdown options have reasonably fresh data
 * ready before the user picks one - each individual range still only
 * actually fetches if it's stale (or `force` is true).
 */
export function refreshAllRangeCaches(force = false) {
  ALL_RANGES.forEach(days => refreshRangeCache(days, force));
}

/**
 * Hook for reading a range's totals from the cache. Triggers a refresh (if
 * stale) on mount/when `days` changes, and re-renders whenever any cache
 * update happens - including updates triggered by a *different* component's
 * refresh of the same range.
 *
 * Returns whatever is currently cached immediately (an empty, incomplete
 * result before the first fetch resolves), so callers never need to handle
 * a loading state explicitly.
 */
export function useRangeTotals(days: number): RangeTotals {
  const [, setTick] = useState(0);

  useEffect(() => {
    const listener = () => setTick(tick => tick + 1);
    listeners.add(listener);
    refreshRangeCache(days);
    return () => {
      listeners.delete(listener);
    };
  }, [days]);

  return cache.get(days) ?? { totals: {}, complete: false };
}
