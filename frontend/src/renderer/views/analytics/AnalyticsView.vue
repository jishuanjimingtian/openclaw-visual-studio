<template>
  <div class="page analytics-page">
    <HeaderToolbar>
      <n-text v-if="lastRefreshedLabel" depth="3" class="refresh-hint">{{ lastRefreshedLabel }}</n-text>
      <n-select
        v-model:value="tokenRange"
        :options="rangeOptions"
        style="width: 120px"
        size="small"
      />
      <n-button :loading="store.loading" size="small" @click="refreshAll">
        <template #icon><n-icon><RefreshOutline /></n-icon></template>
        刷新
      </n-button>
    </HeaderToolbar>

    <div class="page-body analytics-shell">
      <AnalyticsOverviewSection
        :ws-connected="gatewayStore.wsConnected"
        :data-source-hint="dataSourceHint"
        :range-label="rangeLabel"
        :gateway-tokens-loading="store.gatewayTokensLoading"
        :overview-kpis="overviewKpis"
        :range-summary="rangeSummary"
      />

      <div class="analytics-layout">
        <div class="analytics-main">
          <AnalyticsTokenChartCard
            :loading="store.tokenLoading"
            :has-data="hasTokenData"
            @chart-mount="onTokenChartMount"
          />

          <AnalyticsTablesPanel
            v-model:active-tab="activeTableTab"
            :daily-data="dailyTableData"
            :model-data="store.modelData"
            :top-sessions="store.topSessions"
            :breakdown-rows="store.sourceBreakdown?.rows ?? []"
            :token-loading="store.tokenLoading"
            :model-loading="store.modelLoading"
            :top-sessions-loading="store.topSessionsLoading"
            :breakdown-loading="store.breakdownLoading"
            :has-token-data="hasTokenData"
            :has-model-data="hasModelData"
            :ws-connected="gatewayStore.wsConnected"
            :gateway-connected="store.sourceBreakdown?.gatewayConnected ?? false"
          />
        </div>

        <aside class="analytics-aside">
          <AnalyticsDistributionPanel :loading="store.modelLoading" />
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { NIcon, NButton, NSelect, NText } from 'naive-ui';
import { RefreshOutline } from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import { useThemeStore } from '@/stores/theme';
import { ANALYTICS_RANGE_OPTIONS } from './constants';
import { useAnalyticsOverview } from './composables/useAnalyticsOverview';
import { useAnalyticsCharts } from './composables/useAnalyticsCharts';
import AnalyticsOverviewSection from './components/AnalyticsOverviewSection.vue';
import AnalyticsTokenChartCard from './components/AnalyticsTokenChartCard.vue';
import AnalyticsDistributionPanel from './components/AnalyticsDistributionPanel.vue';
import AnalyticsTablesPanel from './components/AnalyticsTablesPanel.vue';

const tokenRange = ref(14);
const activeTableTab = ref('daily');
const rangeOptions = ANALYTICS_RANGE_OPTIONS.map((o) => ({ ...o }));

const themeStore = useThemeStore();
const {
  store,
  gatewayStore,
  rangeLabel,
  dailyTableData,
  rangeSummary,
  dataSourceHint,
  overviewKpis,
  lastRefreshedLabel,
  hasTokenData,
  hasModelData,
} = useAnalyticsOverview(tokenRange);

const {
  tokenChartRef,
  scheduleChartRender,
  resizeCharts,
  disposeCharts,
} = useAnalyticsCharts();

function onTokenChartMount(el: HTMLElement | null) {
  tokenChartRef.value = el;
  scheduleChartRender();
}

async function refreshAll() {
  await store.fetchAllProgressive(tokenRange.value);
  scheduleChartRender();
}

watch(tokenRange, async () => {
  await store.fetchTokenUsage(tokenRange.value, true);
  scheduleChartRender();
});

watch(
  () => [store.tokenData.length, store.modelData.length, hasTokenData.value, hasModelData.value],
  () => scheduleChartRender(),
);

watch(() => themeStore.resolvedTheme, () => scheduleChartRender());

onMounted(async () => {
  gatewayStore.fetchStatus().catch(() => {});
  await refreshAll();
  window.addEventListener('resize', resizeCharts);
});

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts);
  disposeCharts();
});
</script>

<!-- 样式需作用于子组件，不可 scoped -->
<style lang="scss">
@use './analytics.scss';
</style>
