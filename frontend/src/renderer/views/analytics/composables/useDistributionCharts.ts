import { ref, onUnmounted, nextTick } from 'vue';
import { echarts, type ECharts } from '@/utils/echartsCore';
import { useThemeStore } from '@/stores/theme';
import { useAnalyticsStore } from '@/stores/analytics';
import { formatNum, shortModel } from '../format';
import { MODEL_CHART_PALETTE } from '../constants';

export interface DistSlice {
  name: string;
  fullName?: string;
  value: number;
  color: string;
  pct?: number;
}

export interface DistChartSpec {
  id: string;
  title: string;
  slices: DistSlice[];
  valueLabel: string;
}

function cssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

function getPieTheme(isDark: boolean) {
  return {
    textColor: cssVar('--oc-session-text-secondary', isDark ? '#cbd5e1' : '#475569'),
    tooltipBg: cssVar('--oc-surface-elevated', isDark ? '#1c2738' : '#f6fafb'),
    tooltipBorder: cssVar('--oc-stat-border', isDark ? 'rgba(61, 217, 201, 0.12)' : 'rgba(11, 127, 120, 0.1)'),
    pieBorder: cssVar('--oc-surface', isDark ? '#141c2b' : '#ffffff'),
  };
}

export function useDistributionCharts() {
  const store = useAnalyticsStore();
  const themeStore = useThemeStore();
  const chartMap = ref<Map<string, ECharts>>(new Map());
  const hostMap = new Map<string, HTMLElement>();
  let renderTimer: ReturnType<typeof setTimeout> | null = null;
  let resizeObserver: ResizeObserver | null = null;

  function buildSpecs(): DistChartSpec[] {
    const specs: DistChartSpec[] = [];
    const models = store.modelData;

    if (models.length > 0) {
      specs.push({
        id: 'session',
        title: '会话占比',
        valueLabel: '会话',
        slices: models.map((d, i) => ({
          name: shortModel(d.model),
          fullName: d.model,
          value: d.sessionCount,
          color: MODEL_CHART_PALETTE[i % MODEL_CHART_PALETTE.length],
          pct: d.percentage,
        })),
      });

      const tokenSlices = models
        .filter((d) => (d.totalTokens ?? 0) > 0)
        .map((d, i) => ({
          name: shortModel(d.model),
          fullName: d.model,
          value: d.totalTokens ?? 0,
          color: MODEL_CHART_PALETTE[i % MODEL_CHART_PALETTE.length],
          pct: d.tokenPercentage,
        }));
      if (tokenSlices.length > 0) {
        specs.push({
          id: 'token',
          title: 'Token 占比',
          valueLabel: 'Token',
          slices: tokenSlices,
        });
      }

      const msgSlices = models
        .filter((d) => (d.messageCount ?? 0) > 0)
        .map((d, i) => ({
          name: shortModel(d.model),
          fullName: d.model,
          value: d.messageCount ?? 0,
          color: MODEL_CHART_PALETTE[i % MODEL_CHART_PALETTE.length],
        }));
      if (msgSlices.length > 0) {
        const total = msgSlices.reduce((s, x) => s + x.value, 0);
        specs.push({
          id: 'message',
          title: '消息占比',
          valueLabel: '消息',
          slices: msgSlices.map((s) => ({
            ...s,
            pct: total > 0 ? Math.round(s.value * 1000 / total) / 10 : 0,
          })),
        });
      }
    }

    return specs.filter((s) => s.slices.length > 0);
  }

  function registerHost(id: string, el: Element | null) {
    if (el) {
      hostMap.set(id, el as HTMLElement);
    } else {
      hostMap.delete(id);
      const chart = chartMap.value.get(id);
      chart?.dispose();
      chartMap.value.delete(id);
    }
    scheduleRender();
  }

  function renderPie(id: string, spec: DistChartSpec) {
    const el = hostMap.get(id);
    if (!el || spec.slices.length === 0) return;

    let chart = chartMap.value.get(id);
    if (chart && chart.getDom() !== el) {
      chart.dispose();
      chart = undefined;
    }
    if (!chart) {
      chart = echarts.init(el);
      chartMap.value.set(id, chart);
    }

    const theme = getPieTheme(themeStore.resolvedTheme === 'dark');
    chart.setOption({
      tooltip: {
        trigger: 'item',
        backgroundColor: theme.tooltipBg,
        borderColor: theme.tooltipBorder,
        textStyle: { color: theme.textColor, fontSize: 11 },
        formatter: (p: { name: string; value: number; percent: number; data?: { fullName?: string } }) => {
          const full = (p.data as { fullName?: string })?.fullName ?? p.name;
          const val = spec.valueLabel === 'Token' ? formatNum(p.value) : String(p.value);
          return `${full}<br/>${val} (${p.percent}%)`;
        },
      },
      series: [{
        type: 'pie',
        radius: ['38%', '58%'],
        center: ['50%', '50%'],
        minShowLabelAngle: 6,
        itemStyle: {
          borderRadius: 3,
          borderColor: theme.pieBorder,
          borderWidth: 1,
        },
        label: {
          show: true,
          position: 'outside',
          alignTo: 'labelLine',
          fontSize: 10,
          lineHeight: 14,
          color: theme.textColor,
          formatter: (p: { name: string; value: number; percent: number }) => {
            const val = spec.valueLabel === 'Token' ? formatNum(p.value) : String(p.value);
            return `${p.name}\n${val} · ${p.percent.toFixed(1)}%`;
          },
        },
        labelLine: {
          show: true,
          length: 8,
          length2: 10,
          smooth: true,
          lineStyle: { color: theme.textColor, opacity: 0.35, width: 1 },
        },
        data: spec.slices.map((s) => ({
          value: s.value,
          name: s.name,
          fullName: s.fullName,
          itemStyle: { color: s.color },
        })),
      }],
    }, true);
    chart.resize();
  }

  async function renderAll() {
    await nextTick();
    const specs = buildSpecs();
    for (const spec of specs) {
      await new Promise<void>((r) => requestAnimationFrame(() => r()));
      renderPie(spec.id, spec);
    }
    chartMap.value.forEach((chart, id) => {
      if (!specs.some((s) => s.id === id)) {
        chart.dispose();
        chartMap.value.delete(id);
      }
    });
  }

  function scheduleRender() {
    if (renderTimer) clearTimeout(renderTimer);
    renderTimer = setTimeout(() => {
      renderTimer = null;
      void renderAll();
    }, 80);
  }

  function resizeAll() {
    chartMap.value.forEach((c) => c.resize());
  }

  function bindResize() {
    resizeObserver?.disconnect();
    resizeObserver = new ResizeObserver(() => resizeAll());
    hostMap.forEach((el) => resizeObserver?.observe(el));
  }

  function disposeAll() {
    if (renderTimer) clearTimeout(renderTimer);
    resizeObserver?.disconnect();
    resizeObserver = null;
    chartMap.value.forEach((c) => c.dispose());
    chartMap.value.clear();
    hostMap.clear();
  }

  onUnmounted(disposeAll);

  return {
    buildSpecs,
    registerHost,
    scheduleRender,
    resizeAll,
    bindResize,
    disposeAll,
  };
}
