<template>
  <n-card size="small" class="analytics-chart-card analytics-chart-card--metric oc-stat-card" :bordered="false">
    <template #header>
      <n-text strong>数据明细</n-text>
    </template>
    <n-tabs v-model:value="activeTab" type="segment" size="small" class="analytics-table-tabs">
      <n-tab-pane name="daily" tab="每日" display-directive="show">
        <AnalyticsDailyTable
          :data="dailyData"
          :loading="tokenLoading"
          :has-data="hasTokenData"
          :ws-connected="wsConnected"
        />
      </n-tab-pane>
      <n-tab-pane name="model" tab="模型" display-directive="show">
        <AnalyticsModelTable
          :data="modelData"
          :loading="modelLoading"
          :has-data="hasModelData"
        />
      </n-tab-pane>
      <n-tab-pane name="sessions" tab="会话 Top" display-directive="show">
        <AnalyticsTopSessionsTable
          :data="topSessions"
          :loading="topSessionsLoading"
          :has-data="topSessions.length > 0"
        />
      </n-tab-pane>
      <n-tab-pane name="breakdown" tab="来源对账" display-directive="show">
        <AnalyticsBreakdownTable
          :rows="breakdownRows"
          :loading="breakdownLoading"
          :has-data="breakdownRows.length > 0"
          :gateway-connected="gatewayConnected"
        />
      </n-tab-pane>
    </n-tabs>
  </n-card>
</template>

<script setup lang="ts">
import { NCard, NText, NTabs, NTabPane } from 'naive-ui';
import type { ModelUsage, TopSessionUsage, AnalyticsSourceBreakdownRow } from '@shared/types';
import type { DailyTableRow } from '../types';
import AnalyticsDailyTable from './AnalyticsDailyTable.vue';
import AnalyticsModelTable from './AnalyticsModelTable.vue';
import AnalyticsTopSessionsTable from './AnalyticsTopSessionsTable.vue';
import AnalyticsBreakdownTable from './AnalyticsBreakdownTable.vue';

defineProps<{
  dailyData: DailyTableRow[];
  modelData: ModelUsage[];
  topSessions: TopSessionUsage[];
  breakdownRows: AnalyticsSourceBreakdownRow[];
  tokenLoading: boolean;
  modelLoading: boolean;
  topSessionsLoading: boolean;
  breakdownLoading: boolean;
  hasTokenData: boolean;
  hasModelData: boolean;
  wsConnected: boolean;
  gatewayConnected: boolean;
}>();

const activeTab = defineModel<string>('activeTab', { default: 'daily' });
</script>
