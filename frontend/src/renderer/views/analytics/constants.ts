import { ocColors } from '@/utils/chartColors';

export const ANALYTICS_RANGE_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 14 天', value: 14 },
  { label: '近 30 天', value: 30 },
] as const;

export const MODEL_CHART_PALETTE = [
  ocColors.primary,
  ocColors.success,
  ocColors.warning,
  ocColors.error,
  ocColors.metric,
  ocColors.accent,
];
