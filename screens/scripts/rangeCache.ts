import { useEffect, useState } from 'react';
import { getRangeTotals, RANGE_DAYS, RangeTotals } from './rangeStats';

const ALL_RANGES = [RANGE_DAYS.today, RANGE_DAYS.week, RANGE_DAYS.month, RANGE_DAYS.year, RANGE_DAYS.lifetime];

const STALE_MS: Record<number, number> = {
  [RANGE_DAYS.today]: 5 * 1000,
  [RANGE_DAYS.week]: 30 * 1000,
  [RANGE_DAYS.month]: 2 * 60 * 1000,
  [RANGE_DAYS.year]: 5 * 60 * 1000,
  [RANGE_DAYS.lifetime]: 5 * 60 * 1000,
};

const cache = new Map<number, RangeTotals>();
const fetchedAt = new Map<number, number>();
const inFlight = new Map<number, Promise<RangeTotals>>();
const listeners = new Set<() => void>();

function notify() {
  listeners.forEach(listener => listener());
}

function isStale(days: number): boolean {
  const last = fetchedAt.get(days);
  if (last === undefined) {
    return true;
  }
  return Date.now() - last > (STALE_MS[days] ?? 60 * 1000);
}

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
      return cache.get(days) ?? { totals: {}, complete: false };
    });

  inFlight.set(days, request);
  return request;
}

export function refreshRangeCache(days: number, force = false) {
  if (force || isStale(days)) {
    load(days);
  }
}

export function refreshAllRangeCaches(force = false) {
  ALL_RANGES.forEach(days => refreshRangeCache(days, force));
}

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
