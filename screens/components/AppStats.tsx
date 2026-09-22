import React, { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Colors } from '../scripts/colors';
import { barColor, cardStyles, MONITORED_APPS } from '../scripts/chart';
import { RANGE_DAYS } from '../scripts/rangeStats';
import { useRangeTotals } from '../scripts/rangeCache';
import { RangeDropdown } from './RangeDropdown';

const TICK_LADDER = [5, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000];
const MIN_TICKS = 4;
const MAX_TICKS = 6;

const RANGE_OPTIONS = [
  { label: 'Today', value: RANGE_DAYS.today },
  { label: 'This week', value: RANGE_DAYS.week },
  { label: 'This month', value: RANGE_DAYS.month },
  { label: 'This year', value: RANGE_DAYS.year },
  { label: 'Lifetime', value: RANGE_DAYS.lifetime },
];

export const AppStats = () => {
  const [selectedDays, setSelectedDays] = useState(RANGE_DAYS.today);
  const { totals: rangeCounts } = useRangeTotals(selectedDays);

  const totals = MONITORED_APPS.map(app => Number(rangeCounts[app.pkg]) || 0);
  const ticks = buildTicks(Math.max(...totals));

  return (
    <View style={cardStyles.card}>
      {/* --- HEADING + RANGE DROPDOWN --- */}
      <View style={styles.header}>
        <Text style={[cardStyles.title, styles.headerTitle]}>Apps</Text>
        <RangeDropdown options={RANGE_OPTIONS} value={selectedDays} onChange={setSelectedDays} />
      </View>

      {/* --- CHART --- */}
      <View style={styles.chart}>
        <View style={styles.names}>
          {MONITORED_APPS.map(app => (
            <View key={app.pkg} style={styles.nameCell}>
              <Text numberOfLines={1} adjustsFontSizeToFit style={styles.name}>
                {app.label}
              </Text>
            </View>
          ))}
        </View>

        <View style={styles.plot}>
          {totals.map((value, index) => (
            <View key={MONITORED_APPS[index].pkg} style={styles.row}>
              <View
                style={[
                  styles.bar,
                  {
                    width: `${positionOf(value, ticks) * 100}%`,
                    backgroundColor: barColor(index),
                  },
                ]}
              />
            </View>
          ))}
        </View>

        <View style={styles.values}>
          {totals.map((value, index) => (
            <View key={MONITORED_APPS[index].pkg} style={styles.valueCell}>
              <Text numberOfLines={1} adjustsFontSizeToFit style={styles.value}>
                {value}
              </Text>
            </View>
          ))}
        </View>
      </View>

      <View style={styles.axis}>
        <View style={styles.axisSpacer} />
        <View style={styles.ticks}>
          {ticks.map(tick => (
            <Text
              key={tick}
              numberOfLines={1}
              adjustsFontSizeToFit
              style={[cardStyles.axisLabel, styles.tick]}
            >
              {tick}
            </Text>
          ))}
        </View>
      </View>
    </View>
  );
};

// Builds the axis tick values (e.g. 5, 10, 50, 100, 200) for the current
// data: shows up to MAX_TICKS ticks, always reaching far enough to cover
// the largest bar.
function buildTicks(maxValue: number): number[] {
  const firstReaching = TICK_LADDER.findIndex(tick => tick >= maxValue);
  const lastIndex = firstReaching === -1 ? TICK_LADDER.length - 1 : firstReaching;
  const endIndex = Math.max(lastIndex, MIN_TICKS - 1);
  const startIndex = Math.max(0, endIndex - (MAX_TICKS - 1));
  return TICK_LADDER.slice(startIndex, endIndex + 1);
}

// Turns a raw count into a 0-1 fraction of the axis width, walking the tick
// ladder so equal gaps on screen represent equal steps between ticks rather
// than a plain linear scale (which would make small apps invisible).
function positionOf(value: number, ticks: number[]): number {
  if (!Number.isFinite(value) || value <= 0) {
    return 0;
  }
  let lower = 0;
  for (let i = 0; i < ticks.length; i++) {
    if (value <= ticks[i]) {
      return (i + (value - lower) / (ticks[i] - lower)) / ticks.length;
    }
    lower = ticks[i];
  }
  return 1;
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  headerTitle: {
    marginBottom: 0,
  },
  chart: {
    flexDirection: 'row',
    aspectRatio: 3.5,
  },
  names: {
    width: '18%',
  },
  nameCell: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'flex-end',
    paddingRight: '10%',
  },
  name: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 12,
    opacity: 0.7,
  },
  plot: {
    flex: 1,
    borderLeftWidth: 2,
    borderLeftColor: Colors.text,
  },
  values: {
    width: '13%',
  },
  valueCell: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'flex-end',
    paddingLeft: '6%',
  },
  value: {
    color: Colors.text,
    fontFamily: 'WorkSans-SemiBold',
    fontSize: 12,
    opacity: 0.7,
  },
  row: {
    flex: 1,
    justifyContent: 'center',
  },
  bar: {
    height: '55%',
    minWidth: '1.5%',
    borderTopRightRadius: 6,
    borderBottomRightRadius: 6,
  },
  axis: {
    flexDirection: 'row',
  },
  axisSpacer: {
    width: '18%',
  },
  ticks: {
    flex: 1,
    flexDirection: 'row',
    borderTopWidth: 2,
    borderTopColor: Colors.text,
    paddingTop: '2%',
  },
  tick: {
    flex: 1,
    textAlign: 'right',
  },
});
