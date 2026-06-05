<template>
  <div v-if="hasData" class="analytics-table-wrap">
    <n-data-table
      :columns="columns"
      :data="data"
      :bordered="false"
      size="small"
      :pagination="false"
      :row-key="(r: DailyTableRow) => r.date"
    />
  </div>
  <n-empty v-else-if="!loading" description="暂无数据" size="small" />
  <div v-else class="table-state"><n-spin size="small" /></div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NDataTable, NEmpty, NSpin } from 'naive-ui';
import { createDailyColumns } from '../tableColumns';
import type { DailyTableRow } from '../types';

const props = defineProps<{
  data: DailyTableRow[];
  loading: boolean;
  hasData: boolean;
  wsConnected: boolean;
}>();

const columns = computed(() => createDailyColumns(props.wsConnected));
</script>
