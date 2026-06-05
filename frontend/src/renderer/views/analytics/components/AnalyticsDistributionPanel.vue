<template>
  <n-card
    size="small"
    class="analytics-chart-card analytics-chart-card--accent analytics-dist-panel oc-stat-card"
    :bordered="false"
  >
    <template #header>
      <n-text strong>分布概览</n-text>
    </template>

    <div v-if="loading && specs.length === 0" class="analytics-dist-empty">
      <n-spin size="small" />
    </div>
    <n-empty
      v-else-if="specs.length === 0"
      description="暂无分布数据"
      size="small"
      class="analytics-dist-empty"
    />
    <div v-else class="analytics-dist-grid">
      <section
        v-for="spec in specs"
        :key="spec.id"
        class="analytics-dist-block"
      >
        <div class="analytics-dist-head">
          <span class="analytics-dist-title">{{ spec.title }}</span>
          <span class="analytics-dist-total">{{ totalLabel(spec) }}</span>
        </div>
        <div
          :ref="(el) => registerHost(spec.id, el)"
          class="chart-host chart-host--dist"
        />
      </section>
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { computed, watch, onMounted, onUnmounted } from 'vue';
import { NCard, NEmpty, NSpin, NText } from 'naive-ui';
import { useThemeStore } from '@/stores/theme';
import { useAnalyticsStore } from '@/stores/analytics';
import { formatNum } from '../format';
import {
  useDistributionCharts,
  type DistChartSpec,
} from '../composables/useDistributionCharts';

defineProps<{
  loading: boolean;
}>();

const store = useAnalyticsStore();
const themeStore = useThemeStore();
const { buildSpecs, registerHost, scheduleRender, resizeAll, bindResize } = useDistributionCharts();

const specs = computed(() => buildSpecs());

function totalLabel(spec: DistChartSpec): string {
  const sum = spec.slices.reduce((s, x) => s + x.value, 0);
  if (spec.valueLabel === 'Token') return formatNum(sum);
  return String(sum);
}

watch(
  () => [store.modelData.length, themeStore.resolvedTheme],
  () => {
    scheduleRender();
    bindResize();
  },
);

onMounted(() => {
  scheduleRender();
  bindResize();
  window.addEventListener('resize', resizeAll);
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeAll);
});
</script>
