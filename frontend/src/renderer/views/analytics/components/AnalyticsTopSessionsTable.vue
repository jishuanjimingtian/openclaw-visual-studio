<template>
  <div v-if="hasData" class="analytics-table-wrap">
    <n-data-table
      :columns="topSessionColumns"
      :data="data"
      :bordered="false"
      size="small"
      :pagination="false"
      :row-key="(r: TopSessionUsage) => `${r.source}-${r.id}`"
      :row-props="rowProps"
    />
  </div>
  <n-empty v-else-if="!loading" description="暂无会话数据" size="small" />
  <div v-else class="table-state"><n-spin size="small" /></div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { NDataTable, NEmpty, NSpin } from 'naive-ui';
import type { TopSessionUsage } from '@shared/types';
import { topSessionColumns } from '../tableColumns';

defineProps<{
  data: TopSessionUsage[];
  loading: boolean;
  hasData: boolean;
}>();

const router = useRouter();

function rowProps(row: TopSessionUsage) {
  return {
    style: 'cursor: pointer',
    onClick: () => router.push('/chat'),
    title: row.title,
  };
}
</script>
