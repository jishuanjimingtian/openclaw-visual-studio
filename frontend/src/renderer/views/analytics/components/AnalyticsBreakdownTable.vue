<template>
  <div v-if="hasData" class="analytics-table-wrap">
    <n-data-table
      :columns="columns"
      :data="rows"
      :bordered="false"
      size="small"
      :pagination="false"
      :row-key="(r: AnalyticsSourceBreakdownRow) => r.metric"
    />
  </div>
  <n-empty v-else-if="!loading" description="暂无对账数据" size="small" />
  <div v-else class="table-state"><n-spin size="small" /></div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NDataTable, NEmpty, NSpin } from 'naive-ui';
import type { AnalyticsSourceBreakdownRow } from '@shared/types';
import { createBreakdownColumns } from '../tableColumns';

const props = defineProps<{
  rows: AnalyticsSourceBreakdownRow[];
  loading: boolean;
  hasData: boolean;
  gatewayConnected: boolean;
}>();

const columns = computed(() => createBreakdownColumns(props.gatewayConnected));
</script>
