<template>
  <div v-if="hasData" class="analytics-table-wrap">
    <n-data-table
      :columns="modelColumns"
      :data="data"
      :bordered="false"
      size="small"
      :pagination="false"
      :row-key="(r: ModelUsage) => r.model"
    />
  </div>
  <n-empty v-else-if="!loading" description="暂无模型数据" size="small" />
  <div v-else class="table-state"><n-spin size="small" /></div>
</template>

<script setup lang="ts">
import { NDataTable, NEmpty, NSpin } from 'naive-ui';
import type { ModelUsage } from '@shared/types';
import { modelColumns } from '../tableColumns';

defineProps<{
  data: ModelUsage[];
  loading: boolean;
  hasData: boolean;
}>();
</script>
