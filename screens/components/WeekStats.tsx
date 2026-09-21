import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Colors } from '../scripts/colors';
import { DayCounts, formatDate, getWeekHistory, WeekHistory } from '../scripts/stats';

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const CHART_HEIGHT = 120;

export const WeekStats = () => {
  const [history, setHistory] = useState<WeekHistory>({});

  useEffect(() => {
    let cancelled = false;

    getWeekHistory().then(data => {
      if (!cancelled) {
        setHistory(data);
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  const weekDates = getCurrentWeekDates();
  const dailyTotals = weekDates.map(date => totalForDay(history[date]));
  const maxValue = niceMax(Math.max(...dailyTotals));

  return (
    <View style={styles.card}>
      <Text style={styles.title}>This week</Text>

      <View style={styles.chartRow}>
        <View style={styles.yAxis}>
          <Text style={styles.axisLabel}>{maxValue}</Text>
          <Text style={styles.axisLabel}>{Math.round(maxValue / 2)}</Text>
          <Text style={styles.axisLabel}>0</Text>
        </View>

        <View style={styles.bars}>
          {dailyTotals.map((value, index) => {
            const heightPercent = (value / maxValue) * 100;
            const barColor = index % 2 === 0 ? Colors.green : Colors.orange;

            return (
              <View key={weekDates[index]} style={styles.barColumn}>
                <View style={styles.barTrack}>
                  <View
                    style={[
                      styles.bar,
                      { height: `${heightPercent}%`, backgroundColor: barColor },
                    ]}
                  />
                </View>
                <Text style={styles.dayLabel}>{DAY_LABELS[index]}</Text>
              </View>
            );
          })}
        </View>
      </View>
    </View>
  );
};

// ---------------------------------------------------------------- data helpers

/** The current calendar week, Monday first, as "yyyy-MM-dd" strings - 7 entries, in order. */
function getCurrentWeekDates(): string[] {
  const today = new Date();

  // getDay() is 0=Sun..6=Sat. Converting to 1=Mon..7=Sun makes "days since Monday" a
  // straight subtraction, including for Sunday itself.
  const isoWeekday = today.getDay() === 0 ? 7 : today.getDay();
  const monday = new Date(today);
  monday.setDate(today.getDate() - (isoWeekday - 1));

  const dates: string[] = [];
  for (let i = 0; i < 7; i++) {
    const day = new Date(monday);
    day.setDate(monday.getDate() + i);
    dates.push(formatDate(day));
  }
  return dates;
}

/** Sums every monitored app's count for one day into a single "scrolls that day" number. */
function totalForDay(counts: DayCounts | undefined): number {
  if (!counts) {
    return 0;
  }
  return Object.values(counts).reduce((sum, count) => sum + (Number(count) || 0), 0);
}

/**
 * Rounds a raw maximum up to a "nice" number (10, 20, 50, 100, 200, ...) so the y-axis never
 * shows an odd value like "73". Always at least 10, so an empty week still renders a chart
 * instead of dividing by zero.
 */
function niceMax(rawMax: number): number {
  if (!Number.isFinite(rawMax) || rawMax <= 10) {
    return 10;
  }
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawMax)));
  const normalized = rawMax / magnitude;

  let step = 10;
  if (normalized <= 1) step = 1;
  else if (normalized <= 2) step = 2;
  else if (normalized <= 5) step = 5;

  return step * magnitude;
}

// ------------------------------------------------------------------------ style

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.grey,
    borderRadius: 20,
    padding: 20,
  },
  title: {
    color: Colors.text,
    fontSize: 18,
    fontFamily: 'WorkSans-Bold',
    marginBottom: 16,
  },
  chartRow: {
    flexDirection: 'row',
  },
  yAxis: {
    height: CHART_HEIGHT,
    justifyContent: 'space-between',
    marginRight: 10,
  },
  axisLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    opacity: 0.5,
    fontSize: 12,
  },
  bars: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  barColumn: {
    alignItems: 'center',
    flex: 1,
  },
  barTrack: {
    height: CHART_HEIGHT,
    justifyContent: 'flex-end',
    width: '55%',
  },
  bar: {
    borderRadius: 6,
    minHeight: 4,
    width: '100%',
  },
  dayLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 12,
    marginTop: 8,
    opacity: 0.7,
  },
});
