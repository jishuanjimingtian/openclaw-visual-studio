import apiClient from './client';
import type {
  DashboardOverview,
  DashboardStats,
  SystemMetrics,
  DashboardRecentConversation,
  DashboardRecentDeployment,
} from '@shared/types';

export interface DashboardRecent {
  recentConversations: DashboardRecentConversation[];
  recentDeployments: DashboardRecentDeployment[];
}

export interface DashboardOpenClawMeta {
  openclawConfigPath?: string;
  primaryModelRef?: string | null;
}

type DashboardRequestOptions = { signal?: AbortSignal };

export const dashboardApi = {
  getOverview(options?: DashboardRequestOptions) {
    return apiClient.get<DashboardOverview>('/dashboard/overview', options);
  },

  getStats(options?: DashboardRequestOptions) {
    return apiClient.get<DashboardStats>('/dashboard/stats', options);
  },

  getMetrics(includeGatewayTokens = false, options?: DashboardRequestOptions) {
    return apiClient.get<SystemMetrics>('/dashboard/metrics', {
      params: { includeGatewayTokens },
      ...options,
    });
  },

  getRecent(options?: DashboardRequestOptions) {
    return apiClient.get<DashboardRecent>('/dashboard/recent', options);
  },

  getOpenClawMeta(options?: DashboardRequestOptions) {
    return apiClient.get<DashboardOpenClawMeta>('/dashboard/openclaw-meta', options);
  },

  getTodayTokenUsage(options?: DashboardRequestOptions) {
    return apiClient.get<number>('/dashboard/token-usage', options);
  },
};
