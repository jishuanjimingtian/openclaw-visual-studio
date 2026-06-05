import { defineStore } from 'pinia';
import { ref } from 'vue';
import { analyticsApi } from '@/api/analytics';
import type {
  AnalyticsOverview,
  AnalyticsSourceBreakdown,
  DailyTokenUsage,
  ModelUsage,
  TopSessionUsage,
} from '@shared/types';

export const useAnalyticsStore = defineStore('analytics', () => {
  const overview = ref<AnalyticsOverview | null>(null);
  const tokenData = ref<DailyTokenUsage[]>([]);
  const modelData = ref<ModelUsage[]>([]);
  const topSessions = ref<TopSessionUsage[]>([]);
  const sourceBreakdown = ref<AnalyticsSourceBreakdown | null>(null);

  const loading = ref(false);
  const overviewLoading = ref(false);
  const tokenLoading = ref(false);
  const modelLoading = ref(false);
  const topSessionsLoading = ref(false);
  const breakdownLoading = ref(false);
  const gatewayTokensLoading = ref(false);
  const lastRefreshedAt = ref<Date | null>(null);

  function touchRefreshed() {
    lastRefreshedAt.value = new Date();
  }

  async function fetchOverviewFast() {
    overviewLoading.value = true;
    try {
      const res = await analyticsApi.getOverview(false);
      overview.value = res.data;
      touchRefreshed();
    } finally {
      overviewLoading.value = false;
    }
  }

  async function fetchGatewayTokens() {
    gatewayTokensLoading.value = true;
    try {
      const res = await analyticsApi.getGatewayTokens();
      const patch = res.data;
      if (overview.value) {
        overview.value = {
          ...overview.value,
          totalTokens: patch.totalTokens,
          todayTokens: patch.todayTokens,
          totalMessages: patch.totalMessages || overview.value.totalMessages,
          todayMessages: patch.todayMessages || overview.value.todayMessages,
          totalConversations: patch.totalConversations || overview.value.totalConversations,
          activeModels: patch.activeModels ?? overview.value.activeModels,
        };
      } else {
        overview.value = patch;
      }
      applyOverviewFallbackFromCharts();
      touchRefreshed();
    } catch {
      applyOverviewFallbackFromCharts();
    } finally {
      gatewayTokensLoading.value = false;
    }
  }

  function applyOverviewFallbackFromCharts() {
    if (!overview.value) {
      return;
    }
    const rangeMessages = tokenData.value.reduce((sum, row) => sum + (row.messageCount || 0), 0);
    if ((overview.value.totalMessages ?? 0) === 0 && rangeMessages > 0) {
      overview.value.totalMessages = rangeMessages;
    }
    const todayStr = new Date().toISOString().slice(0, 10);
    const todayFromChart = tokenData.value.find((row) => row.date === todayStr)?.messageCount ?? 0;
    if ((overview.value.todayMessages ?? 0) === 0 && todayFromChart > 0) {
      overview.value.todayMessages = todayFromChart;
    }
    const sessionsFromModels = modelData.value.reduce((sum, row) => sum + (row.sessionCount || 0), 0);
    if ((overview.value.totalConversations ?? 0) === 0 && sessionsFromModels > 0) {
      overview.value.totalConversations = sessionsFromModels;
    }
  }

  async function fetchTokenUsage(days: number, includeGateway = true) {
    tokenLoading.value = true;
    try {
      const res = await analyticsApi.getDailyTokenUsage(days, includeGateway);
      tokenData.value = Array.isArray(res.data) ? res.data : [];
      touchRefreshed();
    } catch {
      tokenData.value = [];
    } finally {
      tokenLoading.value = false;
    }
  }

  async function fetchModelUsage() {
    modelLoading.value = true;
    try {
      const res = await analyticsApi.getModelUsage();
      modelData.value = Array.isArray(res.data) ? res.data : [];
      touchRefreshed();
    } catch {
      modelData.value = [];
    } finally {
      modelLoading.value = false;
    }
  }

  async function fetchTopSessions(limit = 20) {
    topSessionsLoading.value = true;
    try {
      const res = await analyticsApi.getTopSessions(limit);
      topSessions.value = Array.isArray(res.data) ? res.data : [];
      touchRefreshed();
    } catch {
      topSessions.value = [];
    } finally {
      topSessionsLoading.value = false;
    }
  }

  async function fetchSourceBreakdown() {
    breakdownLoading.value = true;
    try {
      const res = await analyticsApi.getSourceBreakdown();
      sourceBreakdown.value = res.data;
      touchRefreshed();
    } catch {
      sourceBreakdown.value = null;
    } finally {
      breakdownLoading.value = false;
    }
  }

  async function fetchAllProgressive(days: number) {
    loading.value = true;
    try {
      await Promise.allSettled([
        fetchOverviewFast(),
        fetchModelUsage(),
        fetchTokenUsage(days, true),
        fetchGatewayTokens(),
        fetchTopSessions(20),
        fetchSourceBreakdown(),
      ]);
      applyOverviewFallbackFromCharts();
    } finally {
      loading.value = false;
    }
  }

  return {
    overview,
    tokenData,
    modelData,
    topSessions,
    sourceBreakdown,
    loading,
    overviewLoading,
    tokenLoading,
    modelLoading,
    topSessionsLoading,
    breakdownLoading,
    gatewayTokensLoading,
    lastRefreshedAt,
    fetchOverviewFast,
    fetchGatewayTokens,
    fetchTokenUsage,
    fetchModelUsage,
    fetchTopSessions,
    fetchSourceBreakdown,
    fetchAllProgressive,
  };
});
