import { StyleSheet } from 'react-native';
import { Colors } from './colors';
import { DayCounts, formatDate } from './stats';

export const MONITORED_APPS: { label: string; pkg: string }[] = [
  { label: 'Instagram', pkg: 'com.instagram.android' },
  { label: 'YouTube', pkg: 'com.google.android.youtube' },
  { label: 'Facebook', pkg: 'com.facebook.katana' },
  { label: 'Snapchat', pkg: 'com.snapchat.android' },
];

export function sumCounts(counts: DayCounts | undefined): number {
  if (!counts) {
    return 0;
  }
  return Object.values(counts).reduce((sum, count) => sum + (Number(count) || 0), 0);
}

export function barColor(index: number): string {
  return index % 2 === 0 ? Colors.green : Colors.orange;
}

export function getCurrentWeekDates(): string[] {
  const today = new Date();
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
