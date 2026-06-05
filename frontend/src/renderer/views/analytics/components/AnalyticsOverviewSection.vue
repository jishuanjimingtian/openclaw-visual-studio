<template>
  <section class="analytics-top">
    <div class="analytics-top-status">
      <div class="analytics-top-leading">
        <span class="analytics-dot" :class="wsConnected ? 'is-on' : 'is-off'" />
        <span class="analytics-top-title">
          {{ wsConnected ? 'Gateway 已连接' : 'Gateway 未连接' }}
        </span>
      </div>
      <div class="analytics-top-tags">
        <n-tag size="small" round :bordered="false">{{ rangeLabel }}</n-tag>
        <n-tag v-if="gatewayTokensLoading" size="small" type="info">同步中</n-tag>
      </div>
    </div>
    <p class="analytics-top-hint">{{ dataSourceHint }}</p>
    <div class="analytics-kpis">
      <div
        v-for="kpi in overviewKpis"
        :key="kpi.label"
        class="analytics-kpi"
        :class="{ 'is-hero': kpi.hero }"
        :style="{ '--kpi-accent': kpi.accent }"
      >
        <span class="analytics-kpi-value">{{ kpi.value }}</span>
        <span class="analytics-kpi-label">{{ kpi.label }}</span>
      </div>
    </div>
    <div v-if="rangeSummary" class="analytics-top-range">
      <span class="analytics-range-chip">
        {{ rangeSummary.label }} Token<strong>{{ rangeSummary.totalTokens }}</strong>
      </span>
      <span class="analytics-range-chip">
        日均<strong>{{ rangeSummary.avgPerDay }}</strong>
      </span>
      <span class="analytics-range-chip">
        消息<strong>{{ rangeSummary.totalMessages }}</strong>
      </span>
      <span class="analytics-range-chip">
        峰值<strong>{{ rangeSummary.peakDay }}</strong>
        <template v-if="rangeSummary.peakTokens"> · {{ rangeSummary.peakTokens }}</template>
      </span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { NTag } from 'naive-ui';
import type { AnalyticsKpiItem, AnalyticsRangeSummary } from '../types';

defineProps<{
  wsConnected: boolean;
  dataSourceHint: string;
  rangeLabel: string;
  gatewayTokensLoading: boolean;
  overviewKpis: AnalyticsKpiItem[];
  rangeSummary: AnalyticsRangeSummary | null;
}>();
</script>
