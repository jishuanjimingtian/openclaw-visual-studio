import { defineStore } from 'pinia';
import { ref } from 'vue';
import { dashboardApi } from '@/api/dashboard';
import type {
  DashboardOverview,
  DashboardStats,
  SystemMetrics,
  DashboardGateway,
  DashboardRecentConversation,
  DashboardRecentDeployment,
} from '@shared/types';
import {
  isValidStats,
  mergeBackendMetrics,
  mergeDashboardStats,
  mergeResourcePercent,
  mergeTokenUsage,
} from '@/utils/dashboardMerge';
import { fetchLocalSystemMetrics, hasLocalSystemMetrics } from '@/utils/localSystemMetrics';
import { useConversationStore } from '@/stores/conversation';
import { useGatewayStore } from '@/stores/gateway';

const STATS_TIMEOUT_MS = 12_000;
const METRICS_TIMEOUT_MS = 10_000;
const TOKEN_TIMEOUT_MS = 20_000;

async function withRetry<T>(fn: () => Promise<T>, attempts = 3, delayMs = 350): Promise<T> {
  let lastError: unknown;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastError = e;
      if (i < attempts - 1) {
        await new Promise((r) => setTimeout(r, delayMs * (i + 1)));
      }
    }
  }
  throw lastError;
}

export const useDashboardStore = defineStore('dashboard', () => {
  const stats = ref<DashboardStats>({
    totalConversations: 0,
    activeModels: 0,
    totalModels: 0,
    activeDeployments: 0,
    totalDeployments: 0,
    installedSkills: 0,
    totalSkills: 0,
    totalMessages: 0,
    messagesToday: 0,
  });

  const metrics = ref<SystemMetrics>({
    cpu: 0,
    memory: 0,
    disk: 0,
    uptime: 0,
    sessionCount: 0,
    tokenUsage: 0,
  });

  const gateway = ref<DashboardGateway>({
    status: 'stopped',
    wsConnected: false,
  });

  const recentConversations = ref<DashboardRecentConversation[]>([]);
  const recentDeployments = ref<DashboardRecentDeployment[]>([]);
  const primaryModelRef = ref<string | null>(null);
  const openclawConfigPath = ref<string | undefined>();
  const loading = ref(false);
  const loadingStats = ref(false);
  const loadingMetrics = ref(false);
  const loadingRecent = ref(false);
  const loadingTokens = ref(false);
  const lastRefreshedAt = ref<Date | null>(null);
  const localMetricsActive = ref(hasLocalSystemMetrics());

  let localMetricsTimer: ReturnType<typeof setInterval> | null = null;

  function touchRefreshed() {
    lastRefreshedAt.value = new Date();
  }

  /** 后端本地库为 0 时，用 Gateway 会话列表补全会话数（与会话管理页一致） */
  async function enrichStatsFromGatewaySessions() {
    if (stats.value.totalConversations > 0 && metrics.value.sessionCount > 0) return;

    const gatewayStore = useGatewayStore();
    if (!gatewayStore.wsConnected && !gatewayStore.processOnline) return;

    try {
      const conversationStore = useConversationStore();
      await conversationStore.fetchOpenClawSessions(undefined, false);
      const count = conversationStore.openclawSessions.length;
      if (count <= 0) return;

      if (stats.value.totalConversations === 0) {
        stats.value = { ...stats.value, totalConversations: count };
      }
      if (metrics.value.sessionCount === 0) {
        metrics.value = { ...metrics.value, sessionCount: count };
      }
      touchRefreshed();
    } catch {
      // optional fallback
    }
  }

  function applyLocalResources(cpu: number, memory: number, disk: number) {
    metrics.value = {
      ...metrics.value,
      cpu: mergeResourcePercent(metrics.value.cpu, cpu),
      memory: mergeResourcePercent(metrics.value.memory, memory),
      disk: mergeResourcePercent(metrics.value.disk, disk),
    };
  }

  async function pollLocalSystemMetrics() {
    const local = await fetchLocalSystemMetrics();
    if (!local) return;
    localMetricsActive.value = true;
    applyLocalResources(local.cpu, local.memory, local.disk);
  }

  function startLocalMetricsPolling(intervalMs = 3000) {
    if (!hasLocalSystemMetrics() || localMetricsTimer) return;
    pollLocalSystemMetrics();
    localMetricsTimer = setInterval(() => {
      pollLocalSystemMetrics();
    }, intervalMs);
  }

  function stopLocalMetricsPolling() {
    if (localMetricsTimer) {
      clearInterval(localMetricsTimer);
      localMetricsTimer = null;
    }
  }

  function applyOverview(data: DashboardOverview) {
    if (isValidStats(data.stats)) {
      stats.value = mergeDashboardStats(stats.value, data.stats);
    }
    metrics.value = mergeBackendMetrics(metrics.value, data.metrics, {
      useBackendResources: !localMetricsActive.value,
    });
    gateway.value = data.gateway;
    recentConversations.value = data.recentConversations ?? [];
    recentDeployments.value = data.recentDeployments ?? [];
    if (data.primaryModelRef != null) primaryModelRef.value = data.primaryModelRef;
    if (data.openclawConfigPath) openclawConfigPath.value = data.openclawConfigPath;
    touchRefreshed();
  }

  async function fetchStats() {
    loadingStats.value = true;
    try {
      const res = await withRetry(() =>
        dashboardApi.getStats({ signal: AbortSignal.timeout(STATS_TIMEOUT_MS) }),
      );
      if (isValidStats(res.data)) {
        stats.value = mergeDashboardStats(stats.value, res.data);
        touchRefreshed();
      }
      await enrichStatsFromGatewaySessions();
    } catch {
      await enrichStatsFromGatewaySessions();
    } finally {
      loadingStats.value = false;
    }
  }

  async function fetchMetrics(includeGatewayTokens = false) {
    loadingMetrics.value = true;
    try {
      const res = await withRetry(() =>
        dashboardApi.getMetrics(includeGatewayTokens, {
          signal: AbortSignal.timeout(METRICS_TIMEOUT_MS),
        }),
      );
      metrics.value = mergeBackendMetrics(metrics.value, res.data, {
        useBackendResources: !localMetricsActive.value,
      });
      touchRefreshed();
      await enrichStatsFromGatewaySessions();
    } catch {
      await enrichStatsFromGatewaySessions();
    } finally {
      loadingMetrics.value = false;
    }
  }

  async function fetchRecent() {
    loadingRecent.value = true;
    try {
      const res = await withRetry(() =>
        dashboardApi.getRecent({ signal: AbortSignal.timeout(STATS_TIMEOUT_MS) }),
      );
      recentConversations.value = res.data.recentConversations ?? [];
      recentDeployments.value = res.data.recentDeployments ?? [];
      touchRefreshed();
    } catch {
      // 保留列表
    } finally {
      loadingRecent.value = false;
    }
  }

  async function fetchOpenClawMeta() {
    try {
      const res = await withRetry(() =>
        dashboardApi.getOpenClawMeta({ signal: AbortSignal.timeout(STATS_TIMEOUT_MS) }),
      );
      primaryModelRef.value = res.data.primaryModelRef ?? null;
      openclawConfigPath.value = res.data.openclawConfigPath;
    } catch {
      // optional
    }
  }

  async function fetchTodayTokens() {
    loadingTokens.value = true;
    try {
      const res = await withRetry(
        () =>
          dashboardApi.getTodayTokenUsage({
            signal: AbortSignal.timeout(TOKEN_TIMEOUT_MS),
          }),
        2,
        500,
      );
      metrics.value = {
        ...metrics.value,
        tokenUsage: mergeTokenUsage(metrics.value.tokenUsage, res.data),
      };
      touchRefreshed();
    } catch {
      // Gateway token optional
    } finally {
      loadingTokens.value = false;
    }
  }

  /** 并行拆分加载：快数据先展示，Gateway Token 单独慢请求。 */
  async function fetchAllProgressive() {
    loading.value = true;
    try {
      await Promise.allSettled([
        fetchStats(),
        fetchMetrics(false),
        fetchRecent(),
        fetchOpenClawMeta(),
        fetchTodayTokens(),
      ]);
    } finally {
      loading.value = false;
    }
  }

  /** 轮询业务统计与会话指标（不含 Gateway Token） */
  async function refreshLight() {
    await Promise.allSettled([fetchStats(), fetchMetrics(false)]);
  }

  async function fetchOverview() {
    loading.value = true;
    try {
      const res = await withRetry(() =>
        dashboardApi.getOverview({ signal: AbortSignal.timeout(TOKEN_TIMEOUT_MS) }),
      );
      applyOverview(res.data);
    } catch {
      await fetchAllProgressive();
    } finally {
      loading.value = false;
    }
  }

  return {
    stats,
    metrics,
    gateway,
    recentConversations,
    recentDeployments,
    primaryModelRef,
    openclawConfigPath,
    loading,
    loadingStats,
    loadingMetrics,
    loadingRecent,
    loadingTokens,
    lastRefreshedAt,
    localMetricsActive,
    pollLocalSystemMetrics,
    startLocalMetricsPolling,
    stopLocalMetricsPolling,
    fetchOverview,
    fetchAllProgressive,
    refreshLight,
    fetchStats,
    fetchMetrics,
    fetchRecent,
    fetchOpenClawMeta,
    fetchTodayTokens,
  };
});
