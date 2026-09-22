import { NativeModules } from 'react-native';
import { DayCounts } from './stats';

const { StatsModule } = NativeModules;

export const RANGE_DAYS = {
  today: 1,
  week: 7,
  month: 30,
  year: 365,
  lifetime: 0,
} as const;

export type RangeTotals = {
  totals: DayCounts;
  complete: boolean;
};

export async function getRangeTotals(days: number): Promise<RangeTotals> {
  if (!StatsModule?.getRangeTotals) {
    return { totals: {}, complete: false };
  }
  try {
    const raw: string = await StatsModule.getRangeTotals(days);
    return JSON.parse(raw);
  } catch {
    return { totals: {}, complete: false };
  }
}
