import { ref, nextTick } from 'vue';
import { echarts, type ECharts } from '@/utils/echartsCore';
import { useThemeStore } from '@/stores/theme';
import { useAnalyticsStore } from '@/stores/analytics';
import { ocColors } from '@/utils/chartColors';
import { formatNum } from '../format';

function cssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

function getChartTheme(isDark: boolean) {
  return {
    textColor: cssVar('--oc-session-text-secondary', isDark ? '#cbd5e1' : '#475569'),
    axisColor: cssVar('--oc-stat-border', isDark ? 'rgba(61, 217, 201, 0.12)' : 'rgba(11, 127, 120, 0.1)'),
    splitColor: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(11, 127, 120, 0.08)',
    tooltipBg: cssVar('--oc-surface-elevated', isDark ? '#1c2738' : '#f6fafb'),
    tooltipBorder: cssVar('--oc-stat-border', isDark ? 'rgba(61, 217, 201, 0.12)' : 'rgba(11, 127, 120, 0.1)'),
    pieBorder: cssVar('--oc-surface', isDark ? '#141c2b' : '#ffffff'),
  };
}

async function whenChartReady(el: HTMLElement | null, fn: () => void, attempts = 12): Promise<void> {
  for (let i = 0; i < attempts; i += 1) {
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    if (el && el.clientWidth > 0 && el.clientHeight > 0) {
      fn();
      return;
    }
  }
}

export function useAnalyticsCharts() {
  const store = useAnalyticsStore();
  const themeStore = useThemeStore();

  const chartRefs = ref<Map<string, ECharts>>(new Map());
  const tokenChartRef = ref<HTMLElement | null>(null);
  let chartRenderTimer: ReturnType<typeof setTimeout> | null = null;
  let resizeObserver: ResizeObserver | null = null;

  function getOrCreateChart(key: string, el: HTMLElement): ECharts {
    let chart = chartRefs.value.get(key);
    if (chart && chart.getDom() !== el) {
      chart.dispose();
      chart = undefined;
    }
    if (!chart) {
      chart = echarts.init(el);
      chartRefs.value.set(key, chart);
    }
    return chart;
  }

  function axisTooltipFormatter(params: unknown): string {
    const rows = Array.isArray(params) ? params : [params];
    const first = rows[0] as { axisValue?: string } | undefined;
    const date = first?.axisValue ?? '';
    const lines = rows.map((p) => {
      const item = p as { seriesName?: string; value?: number; marker?: string };
      const val = item.value ?? 0;
      const formatted = item.seriesName === 'Token' ? formatNum(Number(val)) : String(val);
      return `${item.marker ?? ''} ${item.seriesName ?? ''}: ${formatted}`;
    });
    return `<div style="font-weight:600;margin-bottom:4px">${date}</div>${lines.join('<br/>')}`;
  }

  function initTokenChart() {
    if (!tokenChartRef.value || store.tokenData.length === 0) return;
    const chart = getOrCreateChart('token', tokenChartRef.value);
    const theme = getChartTheme(themeStore.resolvedTheme === 'dark');
    const dates = store.tokenData.map((d) => d.date.slice(5));
    const tokens = store.tokenData.map((d) => d.tokens);
    const msgs = store.tokenData.map((d) => d.messageCount);

    chart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: theme.tooltipBg,
        borderColor: theme.tooltipBorder,
        textStyle: { color: theme.textColor, fontSize: 12 },
        formatter: axisTooltipFormatter,
      },
      legend: {
        data: ['Token', '消息'],
        top: 0,
        right: 0,
        textStyle: { color: theme.textColor, fontSize: 11 },
      },
      grid: { left: 48, right: 40, top: 22, bottom: 40 },
      xAxis: {
        type: 'category',
        name: '日期',
        nameTextStyle: { color: theme.textColor, fontSize: 11 },
        data: dates,
        axisLine: { lineStyle: { color: theme.axisColor } },
        axisLabel: { color: theme.textColor, fontSize: 11 },
      },
      yAxis: [
        {
          type: 'value',
          name: 'Token',
          nameTextStyle: { color: theme.textColor, fontSize: 11 },
          axisLabel: {
            color: theme.textColor,
            fontSize: 11,
            formatter: (v: number) => formatNum(v),
          },
          splitLine: { lineStyle: { color: theme.splitColor, type: 'dashed' } },
        },
        {
          type: 'value',
          name: '消息',
          nameTextStyle: { color: theme.textColor, fontSize: 11 },
          axisLabel: { color: theme.textColor, fontSize: 11 },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: 'Token',
          type: 'bar',
          data: tokens,
          itemStyle: { color: ocColors.primary, borderRadius: [4, 4, 0, 0] },
          emphasis: { focus: 'series' },
        },
        {
          name: '消息',
          type: 'line',
          yAxisIndex: 1,
          data: msgs,
          smooth: true,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: { color: ocColors.warning, width: 2 },
          itemStyle: { color: ocColors.warning },
        },
      ],
    }, true);
    chart.resize();
  }

  function resizeCharts() {
    chartRefs.value.forEach((chart) => chart.resize());
  }

  function bindChartResizeObserver() {
    resizeObserver?.disconnect();
    resizeObserver = new ResizeObserver(() => resizeCharts());
    if (tokenChartRef.value) resizeObserver.observe(tokenChartRef.value);
  }

  async function renderAllCharts() {
    await whenChartReady(tokenChartRef.value, initTokenChart);
  }

  function scheduleChartRender() {
    if (chartRenderTimer) clearTimeout(chartRenderTimer);
    chartRenderTimer = setTimeout(() => {
      chartRenderTimer = null;
      void nextTick(async () => {
        await renderAllCharts();
        resizeCharts();
        bindChartResizeObserver();
      });
    }, 80);
  }

  function disposeCharts() {
    if (chartRenderTimer) clearTimeout(chartRenderTimer);
    resizeObserver?.disconnect();
    resizeObserver = null;
    chartRefs.value.forEach((c) => c.dispose());
    chartRefs.value.clear();
  }

  return {
    tokenChartRef,
    scheduleChartRender,
    resizeCharts,
    disposeCharts,
  };
}
