<template>
  <n-card size="small" class="analytics-chart-card oc-stat-card" :bordered="false">
    <template #header>
      <n-space align="center" :size="6" :wrap="false">
        <n-text strong>Token 趋势</n-text>
        <n-tag v-if="loading" size="tiny" type="info">…</n-tag>
      </n-space>
    </template>
    <div class="analytics-chart-body">
      <div v-if="loading && !hasData" class="chart-state">
        <n-spin size="small" />
      </div>
      <div v-else-if="!hasData" class="chart-state">
        <n-empty description="暂无数据" size="small" />
      </div>
      <div v-else :ref="onChartMount" class="chart-host" />
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { NCard, NEmpty, NSpin, NSpace, NText, NTag } from 'naive-ui';

defineProps<{
  loading: boolean;
  hasData: boolean;
}>();

const emit = defineEmits<{
  chartMount: [HTMLElement | null];
}>();

function onChartMount(el: Element | null) {
  emit('chartMount', el as HTMLElement | null);
}
</script>
