import { computed, type Ref } from 'vue';
import { useAnalyticsStore } from '@/stores/analytics';
import { useGatewayStore } from '@/stores/gateway';
import { formatNum } from '../format';
import { ANALYTICS_RANGE_OPTIONS } from '../constants';
import type { AnalyticsKpiItem, AnalyticsRangeSummary, DailyTableRow } from '../types';

export function useAnalyticsOverview(tokenRange: Ref<number>) {
  const store = useAnalyticsStore();
  const gatewayStore = useGatewayStore();

  const rangeLabel = computed(
    () => ANALYTICS_RANGE_OPTIONS.find((o) => o.value === tokenRange.value)?.label ?? '近 14 天',
  );

  const peakDate = computed(() => {
    if (!store.tokenData.length) return '';
    const peak = store.tokenData.reduce(
      (best, d) => (d.tokens > best.tokens ? d : best),
      store.tokenData[0],
    );
    return peak.date;
  });

  const dailyTableData = computed<DailyTableRow[]>(() => {
    const asc = store.tokenData;
    return [...asc].reverse().map((row) => {
      const ascIdx = asc.findIndex((d) => d.date === row.date);
      const prev = ascIdx > 0 ? asc[ascIdx - 1] : null;
      return {
        ...row,
        tokenDelta: prev ? row.tokens - prev.tokens : null,
        isPeak: row.date === peakDate.value,
      };
    });
  });

  const rangeSummary = computed<AnalyticsRangeSummary | null>(() => {
    if (store.tokenData.length === 0) return null;
    const totalTokens = store.tokenData.reduce((s, d) => s + d.tokens, 0);
    const totalMessages = store.tokenData.reduce((s, d) => s + d.messageCount, 0);
    const days = store.tokenData.length;
    const peak = store.tokenData.find((d) => d.date === peakDate.value) ?? store.tokenData[0];
    const label = ANALYTICS_RANGE_OPTIONS.find((o) => o.value === tokenRange.value)?.label
      ?? `近 ${days} 天`;
    return {
      label,
      totalTokens: formatNum(totalTokens),
      avgPerDay: formatNum(Math.round(totalTokens / days)),
      totalMessages: String(totalMessages),
      peakDay: peak.date.slice(5),
      peakTokens: peak.tokens > 0 ? formatNum(peak.tokens) : '',
    };
  });

  const dataSourceHint = computed(() =>
    gatewayStore.wsConnected
      ? 'Token 与消息统计含 OpenClaw Gateway 对话；本地库会话也会合并计入'
      : '当前仅显示本地数据库统计，连接 Gateway 后可合并 OpenClaw 对话数据',
  );

  const overviewKpis = computed<AnalyticsKpiItem[]>(() => [
    {
      label: '总会话',
      value: store.overview?.totalConversations ?? '—',
      hero: false,
      accent: 'var(--oc-metric)',
    },
    {
      label: '总消息',
      value: formatNum(store.overview?.totalMessages ?? 0),
      hero: false,
      accent: 'var(--oc-success)',
    },
    {
      label: '总 Token',
      value: store.gatewayTokensLoading ? '…' : formatNum(store.overview?.totalTokens ?? 0),
      hero: true,
      accent: 'var(--oc-primary)',
    },
    {
      label: '今日消息',
      value: store.overview?.todayMessages ?? '—',
      hero: false,
      accent: 'var(--oc-accent)',
    },
    {
      label: '今日 Token',
      value: store.gatewayTokensLoading ? '…' : formatNum(store.overview?.todayTokens ?? 0),
      hero: false,
      accent: 'var(--oc-warning)',
    },
  ]);

  const lastRefreshedLabel = computed(() => {
    if (!store.lastRefreshedAt) return '';
    return store.lastRefreshedAt.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }) + ' 更新';
  });

  return {
    store,
    gatewayStore,
    rangeLabel,
    peakDate,
    dailyTableData,
    rangeSummary,
    dataSourceHint,
    overviewKpis,
    lastRefreshedLabel,
    hasTokenData: computed(() => store.tokenData.length > 0),
    hasModelData: computed(() => store.modelData.length > 0),
  };
}
