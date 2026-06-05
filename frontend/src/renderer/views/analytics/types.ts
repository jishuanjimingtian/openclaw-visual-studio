import type { DailyTokenUsage } from '@shared/types';

export interface DailyTableRow extends DailyTokenUsage {
  tokenDelta: number | null;
  isPeak: boolean;
}

export interface AnalyticsRangeSummary {
  label: string;
  totalTokens: string;
  avgPerDay: string;
  totalMessages: string;
  peakDay: string;
  peakTokens: string;
}

export interface AnalyticsKpiItem {
  label: string;
  value: string | number;
  hero: boolean;
  accent: string;
}
