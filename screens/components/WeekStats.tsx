import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Colors } from '../scripts/colors';
import { useWeekHistory } from '../scripts/useWeekHistory';
import { barColor, cardStyles, getCurrentWeekDates, sumCounts } from '../scripts/chart';

const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const CHART_HEIGHT = 100;

export const WeekStats = () => {
  const history = useWeekHistory();

  const weekDates = getCurrentWeekDates();
  const dailyTotals = weekDates.map(date => sumCounts(history[date]));
  const maxValue = niceMax(Math.max(...dailyTotals));

  return (
    <View style={cardStyles.card}>
      <Text style={cardStyles.title}>This week</Text>

      <View style={styles.chartRow}>
        <View style={styles.yAxis}>
          <Text style={cardStyles.axisLabel}>{maxValue}</Text>
          <Text style={cardStyles.axisLabel}>{Math.round(maxValue / 2)}</Text>
          <Text style={cardStyles.axisLabel}>0</Text>
        </View>

        <View style={styles.bars}>
          {dailyTotals.map((value, index) => {
            const heightPercent = (value / maxValue) * 100;

            return (
              <View key={weekDates[index]} style={styles.barColumn}>
                <View style={styles.barTrack}>
                  <View
                    style={[
                      styles.bar,
                      { height: `${heightPercent}%`, backgroundColor: barColor(index) },
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

const styles = StyleSheet.create({
  chartRow: {
    flexDirection: 'row',
  },
  yAxis: {
    height: CHART_HEIGHT,
    justifyContent: 'space-between',
    marginRight: 10,
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
