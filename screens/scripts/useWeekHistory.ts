/**
 * Hook that keeps a component's on-device WeekHistory fresh: fetches once on
 * mount, again every time the app returns to the foreground, and on a
 * regular poll while the app is open. Used by WeekStats, whose Mon-Sun chart
 * needs to reflect scrolls counted just moments ago, not just history as of
 * whenever the screen first opened.
 *
 * This is the on-device counterpart to rangeCache.ts's useRangeTotals() -
 * that one is for the Supabase-backed, user-selectable ranges in
 * CountDisplay/AppStats, this one is for the fixed "recent days" data
 * getWeekHistory() already returns.
 */

import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { getWeekHistory, WeekHistory } from './stats';

/** How often to re-fetch while the app is open and in the foreground. */
const POLL_INTERVAL_MS = 3000;

export function useWeekHistory(): WeekHistory {
  const [history, setHistory] = useState<WeekHistory>({});

  useEffect(() => {
    let active = true;

    const refresh = () => {
      getWeekHistory().then(data => {
        if (!active) {
          return;
        }
        // Skip the re-render when nothing actually changed, so a poll that
        // returns identical data doesn't cause an unnecessary chart redraw.
        setHistory(previous =>
          JSON.stringify(previous) === JSON.stringify(data) ? previous : data,
        );
      });
    };

    refresh();

    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refresh();
      }
    });

    const interval = setInterval(refresh, POLL_INTERVAL_MS);

    return () => {
      active = false;
      subscription.remove();
      clearInterval(interval);
    };
  }, []);

  return history;
}
