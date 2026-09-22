import { useEffect, useState } from 'react';
import { AppState } from 'react-native';
import { getWeekHistory, WeekHistory } from './stats';

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
