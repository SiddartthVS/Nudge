import React, { useCallback, useEffect, useState } from 'react';
import { AppState, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Colors } from '../colors';
import { DayCounts, getTodayCounts } from '../native/stats';
import { useBase, pct } from '../scale';


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
  const base = useBase();
  const [todayCounts, setTodayCounts] = useState<DayCounts>({});
  const [selectedPkg, setSelectedPkg] = useState<string | null>(null);

  const refresh = useCallback(() => {
    getTodayCounts().then(setTodayCounts);
  }, []);

  useEffect(() => {
    refresh();

    // Covers the common case: user scrolls Instagram, then switches back to Nudge - this
    // fires the moment the app becomes visible again, instead of waiting for the next poll.
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
    <View style={[styles.card, { borderRadius: pct(base, 2.7) }]}>

      <View style={styles.content}>
        <Text style={styles.count}>{displayCount}</Text>

        <View style={styles.pills}>
          {FILTERS.map(filter => {
            const active = filter.pkg === selectedPkg;
            return (
              <TouchableOpacity
                key={filter.label}
                onPress={() => setSelectedPkg(filter.pkg)}
                style={[styles.pill, active && styles.pillActive]}
              >
                <Text style={[styles.pillLabel, active && styles.pillLabelActive]}>
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

// ---------------------------------------------------------------------- helpers

function sumAll(counts: DayCounts): number {
  return Object.values(counts).reduce((sum, count) => sum + (Number(count) || 0), 0);
}


// ------------------------------------------------------------------------ style

const styles = StyleSheet.create({

  card: {
    // borderRadius is set in the component as a % of the screen width (see pct), not a fixed 20.
    overflow: 'hidden',
    flex: 1,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 20,
  },
  count: {
    color: Colors.text,
    fontFamily: 'WorkSans-Black',
    fontSize: 102,
  },
  pills: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    marginTop: 16,
    paddingHorizontal: 12,
  },
  pill: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: 999,
    marginHorizontal: 4,
    marginVertical: 4,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  pillActive: {
    backgroundColor: Colors.text,
  },
  pillLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 13,
  },
  pillLabelActive: {
    color: Colors.background,
    fontFamily: 'WorkSans-SemiBold',
  },
});
