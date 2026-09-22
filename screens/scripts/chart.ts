/**
 * Shared building blocks for the three stat cards on the home screen
 * (CountDisplay, WeekStats, AppStats).
 *
 * Anything that more than one card needed - the list of monitored apps, the
 * green/orange bar coloring, the "which 7 dates make up this week" logic, and
 * the card/title/axis-label styling - lives here instead of being copied into
 * each component.
 */

import { StyleSheet } from 'react-native';
import { Colors } from './colors';
import { DayCounts, formatDate } from './stats';

/**
 * The apps NUDGE tracks, in a fixed display order. This order is what keeps
 * each app's color consistent across cards: barColor() below assigns colors
 * by position in this array, so Instagram is always green, YouTube always
 * orange, and so on, everywhere an app list is rendered.
 *
 * The `pkg` values are Android package names and must exactly match the
 * packages TrackerService.java watches on the native side - see
 * android/app/src/main/java/com/nudge/TrackerService.java.
 */
export const MONITORED_APPS: { label: string; pkg: string }[] = [
  { label: 'Instagram', pkg: 'com.instagram.android' },
  { label: 'YouTube', pkg: 'com.google.android.youtube' },
  { label: 'Facebook', pkg: 'com.facebook.katana' },
  { label: 'Snapchat', pkg: 'com.snapchat.android' },
];

/**
 * Adds up every app's count for a single day, e.g. turns
 * { instagram: 10, youtube: 5 } into 15. Used wherever a card needs an
 * "all apps combined" total rather than a per-app breakdown.
 */
export function sumCounts(counts: DayCounts | undefined): number {
  if (!counts) {
    return 0;
  }
  return Object.values(counts).reduce((sum, count) => sum + (Number(count) || 0), 0);
}

/**
 * The alternating green/orange bar color scheme, by position in a list
 * (typically MONITORED_APPS or a week's worth of days). Index 0, 2, 4... are
 * green; 1, 3, 5... are orange.
 */
export function barColor(index: number): string {
  return index % 2 === 0 ? Colors.green : Colors.orange;
}

/**
 * The 7 dates (Monday through Sunday) of the current week, as "yyyy-MM-dd"
 * strings, in the device's local timezone. Weeks always start on Monday
 * regardless of the phone's locale settings, which keeps WeekStats' Mon-Sun
 * chart consistent for every user.
 */
export function getCurrentWeekDates(): string[] {
  const today = new Date();
  // getDay() is 0 (Sunday) through 6 (Saturday). Converting Sunday to 7 makes
  // "days since Monday" a simple subtraction for every day of the week.
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

/**
 * Shared look for the stat cards: the card container itself, its heading
 * ("This week", "Apps", ...), and the small axis tick labels. Defined once
 * here so every card matches without repeating the same style objects.
 */
export const cardStyles = StyleSheet.create({
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
  axisLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    opacity: 0.5,
    fontSize: 12,
  },
});
