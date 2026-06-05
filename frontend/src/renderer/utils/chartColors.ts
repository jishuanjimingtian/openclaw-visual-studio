/** Read OpenClaw design tokens from CSS variables (for Naive/ECharts props). */
export function getOcColor(name: string, fallback = '#0d9488'): string {
  if (typeof document === 'undefined') return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value || fallback;
}

export const ocColors = {
  get primary() {
    return getOcColor('--oc-primary');
  },
  get accent() {
    return getOcColor('--oc-accent');
  },
  get success() {
    return getOcColor('--oc-success');
  },
  get successLight() {
    return getOcColor('--oc-success-light');
  },
  get warning() {
    return getOcColor('--oc-warning');
  },
  get warningLight() {
    return getOcColor('--oc-warning-light');
  },
  get error() {
    return getOcColor('--oc-error');
  },
  get muted() {
    return getOcColor('--oc-muted');
  },
  get metric() {
    return getOcColor('--oc-metric');
  },
  get metricLight() {
    return getOcColor('--oc-metric-light');
  },
};

export function progressColor(value: number, danger: number, warn: number): string {
  if (value >= danger) return ocColors.error;
  if (value >= warn) return ocColors.warning;
  return ocColors.success;
}

export function deploymentProgressColor(percentage: number): string {
  if (percentage >= 80) return ocColors.success;
  if (percentage >= 40) return ocColors.primary;
  return ocColors.warning;
}
