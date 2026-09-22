import React, { useEffect, useState } from 'react';
import { AppState, Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Colors } from '../scripts/colors';
import { MONITORED_APPS, sumCounts } from '../scripts/chart';
import { RANGE_DAYS } from '../scripts/rangeStats';
import { refreshAllRangeCaches, refreshRangeCache, useRangeTotals } from '../scripts/rangeCache';


const POLL_INTERVAL_MS = 3000;

type AppFilter = {
  label: string;
  pkg: string | null;
};

type RangeFilter = {
  label: string;
  days: number;
};

const APP_FILTERS: AppFilter[] = [{ label: 'All', pkg: null }, ...MONITORED_APPS];

const RANGE_FILTERS: RangeFilter[] = [
  { label: 'Lifetime', days: RANGE_DAYS.lifetime },
  { label: 'This year', days: RANGE_DAYS.year },
  { label: 'This month', days: RANGE_DAYS.month },
  { label: 'This week', days: RANGE_DAYS.week },
  { label: 'Today', days: RANGE_DAYS.today },
];

const CountDisplay = () => {
  const [selectedPkg, setSelectedPkg] = useState<string | null>(null);
  const [selectedDays, setSelectedDays] = useState<number>(RANGE_DAYS.today);
  const { totals: rangeCounts } = useRangeTotals(selectedDays);

  useEffect(() => {
    refreshAllRangeCaches(true);

    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refreshAllRangeCaches();
      }
    });

    return () => subscription.remove();
  }, []);

  useEffect(() => {
    const interval = setInterval(() => refreshRangeCache(selectedDays), POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedDays]);

  const displayCount = selectedPkg === null
    ? sumCounts(rangeCounts)
    : rangeCounts[selectedPkg] ?? 0;

  return (
    <View style={styles.card}>
      <Image
        source={require('../../assets/images/bg.png')}
        resizeMode="stretch"
        style={styles.background}
      />

      <View style={styles.content}>
        <View style={styles.rangePills}>
          {RANGE_FILTERS.map(filter => {
            const active = filter.days === selectedDays;
            return (
              <TouchableOpacity
                key={filter.label}
                onPress={() => setSelectedDays(filter.days)}
                style={[styles.pill, { flex: filter.label.length + 4 }, active && styles.pillActive]}
              >
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  minimumFontScale={0.5}
                  style={[styles.pillLabel, active && styles.pillLabelActive]}
                >
                  {filter.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>

        <Text style={styles.count}>{displayCount}</Text>
        <Text style={styles.text}>scrolls!</Text>

        <View style={styles.appPills}>
          {APP_FILTERS.map(filter => {
            const active = filter.pkg === selectedPkg;
            return (
              <TouchableOpacity
                key={filter.label}
                onPress={() => setSelectedPkg(filter.pkg)}
                style={[styles.pill, { flex: filter.label.length + 4 }, active && styles.pillActive]}
              >
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  minimumFontScale={0.5}
                  style={[styles.pillLabel, active && styles.pillLabelActive]}
                >
                  {filter.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>
    </View>
  );
};

export default CountDisplay;


const styles = StyleSheet.create({
  card: {
    borderRadius: '3%',
    overflow: 'hidden',
    flex: 1,
  },
  background: {
    position: 'absolute',
    width: '122%',
    height: '122%',
    left: '-11%',
    top: '-11%',
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: '6%',
  },
  count: {
    color: Colors.text,
    fontFamily: 'WorkSans-Black',
    fontSize: 102,
  },
  text: {
    color: Colors.text,
    marginTop: '-5%',
    fontFamily: 'WorkSans-Medium'
  },
  rangePills: {
    position: 'absolute',
    top: '5%',
    flexDirection: 'row',
    width: '100%',
    paddingHorizontal: '3%',
  },
  appPills: {
    position: 'absolute',
    bottom: '5%',
    flexDirection: 'row',
    width: '100%',
    paddingHorizontal: '3%',
  },
  pill: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: 999,
    marginHorizontal: '0.6%',
    paddingVertical: '1.5%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillActive: {
    backgroundColor: Colors.text,
  },
  pillLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 11,
  },
  pillLabelActive: {
    color: Colors.background,
    fontFamily: 'WorkSans-SemiBold',
  },
});
