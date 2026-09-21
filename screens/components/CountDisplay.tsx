import React, { useCallback, useEffect, useState } from 'react';
import { AppState, Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Colors } from '../scripts/colors';
import { DayCounts, getTodayCounts } from '../scripts/stats';


const POLL_INTERVAL_MS = 3000;

type AppFilter = {
  label: string;
  pkg: string | null;
};

const FILTERS: AppFilter[] = [
  { label: 'All', pkg: null },
  { label: 'Instagram', pkg: 'com.instagram.android' },
  { label: 'YouTube', pkg: 'com.google.android.youtube' },
  { label: 'Facebook', pkg: 'com.facebook.katana' },
  { label: 'Snapchat', pkg: 'com.snapchat.android' },
];

const CountDisplay = () => {
  const [todayCounts, setTodayCounts] = useState<DayCounts>({});
  const [selectedPkg, setSelectedPkg] = useState<string | null>(null);

  const refresh = useCallback(() => {
    getTodayCounts().then(setTodayCounts);
  }, []);

  useEffect(() => {
    refresh();

    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refresh();
      }
    });

    const interval = setInterval(refresh, POLL_INTERVAL_MS);

    return () => {
      subscription.remove();
      clearInterval(interval);
    };
  }, [refresh]);

  const displayCount = selectedPkg === null
    ? sumAll(todayCounts)
    : todayCounts[selectedPkg] ?? 0;

  return (
    <View style={styles.card}>
      <Image
        source={require('../../assets/images/bg.png')}
        resizeMode="stretch"
        style={styles.background}
      />

      <View style={styles.content}>
        <Text style={styles.count}>{displayCount}</Text>
        <Text style={styles.text}>scrolls today</Text>

        <View style={styles.pills}>
          {FILTERS.map(filter => {
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


function sumAll(counts: DayCounts): number {
  return Object.values(counts).reduce((sum, count) => sum + (Number(count) || 0), 0);
}


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
  pills: {
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
