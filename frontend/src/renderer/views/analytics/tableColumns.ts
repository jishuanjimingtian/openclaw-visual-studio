import { h } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import type {
  ModelUsage,
  TopSessionUsage,
  AnalyticsSourceBreakdownRow,
} from '@shared/types';
import type { DailyTableRow } from './types';
import {
  formatNum,
  formatDelta,
  formatCost,
  shortModel,
  formatUpdatedAt,
  sourceLabel,
} from './format';

export function createDailyColumns(wsConnected: boolean): DataTableColumns<DailyTableRow> {
  return [
    {
      title: '日期',
      key: 'date',
      width: 108,
      render: (r) => h('span', { class: r.isPeak ? 'analytics-peak-date' : undefined }, [
        r.isPeak ? '★ ' : '',
        r.date.slice(5),
      ]),
    },
    {
      title: 'Token',
      key: 'tokens',
      render: (r) => formatNum(r.tokens),
      sorter: (a, b) => a.tokens - b.tokens,
    },
    {
      title: '本地',
      key: 'localTokens',
      render: (r) => formatNum(r.localTokens ?? 0),
    },
    {
      title: 'Gateway',
      key: 'gatewayTokens',
      render: (r) => (
        wsConnected && (r.gatewayTokens ?? 0) > 0
          ? formatNum(r.gatewayTokens!)
          : '—'
      ),
    },
    {
      title: '环比',
      key: 'tokenDelta',
      render: (r) => {
        if (r.tokenDelta === null) return '—';
        const cls = r.tokenDelta > 0
          ? 'analytics-delta-up'
          : r.tokenDelta < 0
            ? 'analytics-delta-down'
            : '';
        return h('span', { class: cls }, formatDelta(r.tokenDelta));
      },
    },
    {
      title: '消息',
      key: 'messageCount',
      sorter: (a, b) => a.messageCount - b.messageCount,
    },
    {
      title: '均/条',
      key: 'avg',
      render: (r) => (
        r.messageCount > 0 ? formatNum(Math.round(r.tokens / r.messageCount)) : '—'
      ),
    },
  ];
}

export const modelColumns: DataTableColumns<ModelUsage> = [
  {
    title: '模型',
    key: 'model',
    ellipsis: { tooltip: true },
    render: (r) => shortModel(r.model),
  },
  {
    title: '会话',
    key: 'sessionCount',
    width: 56,
    sorter: (a, b) => a.sessionCount - b.sessionCount,
    defaultSortOrder: 'descend',
  },
  {
    title: '会话%',
    key: 'percentage',
    width: 62,
    render: (r) => `${r.percentage}%`,
  },
  {
    title: 'Token',
    key: 'totalTokens',
    render: (r) => (r.totalTokens != null && r.totalTokens > 0 ? formatNum(r.totalTokens) : '—'),
    sorter: (a, b) => (a.totalTokens ?? 0) - (b.totalTokens ?? 0),
  },
  {
    title: 'Token%',
    key: 'tokenPercentage',
    width: 62,
    render: (r) => (r.tokenPercentage != null ? `${r.tokenPercentage}%` : '—'),
  },
  {
    title: '消息',
    key: 'messageCount',
    width: 56,
    render: (r) => (r.messageCount != null && r.messageCount > 0 ? String(r.messageCount) : '—'),
  },
  {
    title: '均/条',
    key: 'avg',
    width: 56,
    render: (r) => (
      r.messageCount && r.totalTokens
        ? formatNum(Math.round(r.totalTokens / r.messageCount))
        : '—'
    ),
  },
];

export const topSessionColumns: DataTableColumns<TopSessionUsage> = [
  {
    title: '会话',
    key: 'title',
    ellipsis: { tooltip: true },
  },
  {
    title: '模型',
    key: 'model',
    width: 100,
    ellipsis: { tooltip: true },
    render: (r) => shortModel(r.model),
  },
  {
    title: 'Token',
    key: 'totalTokens',
    width: 72,
    render: (r) => formatNum(r.totalTokens),
    sorter: (a, b) => a.totalTokens - b.totalTokens,
  },
  {
    title: '消息',
    key: 'messageCount',
    width: 52,
    render: (r) => (r.messageCount > 0 ? String(r.messageCount) : '—'),
  },
  {
    title: 'In/Out',
    key: 'io',
    width: 80,
    render: (r) => {
      const hasIo = (r.inputTokens ?? 0) > 0 || (r.outputTokens ?? 0) > 0;
      return hasIo
        ? `${formatNum(r.inputTokens ?? 0)}/${formatNum(r.outputTokens ?? 0)}`
        : '—';
    },
  },
  {
    title: '费用',
    key: 'totalCost',
    width: 56,
    render: (r) => formatCost(r.totalCost),
  },
  {
    title: '活跃',
    key: 'updatedAt',
    width: 52,
    render: (r) => formatUpdatedAt(r.updatedAt),
  },
  {
    title: '来源',
    key: 'source',
    width: 68,
    render: (r) => sourceLabel(r.source),
  },
];

export function createBreakdownColumns(
  gatewayConnected: boolean,
): DataTableColumns<AnalyticsSourceBreakdownRow> {
  return [
    { title: '指标', key: 'label', width: 100 },
    {
      title: '本地库',
      key: 'local',
      render: (r) => formatNum(r.local),
    },
    {
      title: 'Gateway',
      key: 'gateway',
      render: (r) => (gatewayConnected ? formatNum(r.gateway) : '—'),
    },
    {
      title: '合计',
      key: 'total',
      render: (r) => formatNum(r.total),
    },
  ];
}
