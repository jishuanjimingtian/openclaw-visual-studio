import apiClient from './client';
import type {
  AnalyticsOverview,
  AnalyticsSourceBreakdown,
  DailyTokenUsage,
  ModelUsage,
  TopSessionUsage,
} from '@shared/types';

export const analyticsApi = {
  getOverview(includeGatewayTokens = false) {
    return apiClient.get<AnalyticsOverview>('/analytics/overview', {
      params: { includeGatewayTokens },
    });
  },

  getGatewayTokens() {
    return apiClient.get<AnalyticsOverview>('/analytics/gateway-tokens');
  },

  getDailyTokenUsage(days = 14, includeGateway = true) {
    return apiClient.get<DailyTokenUsage[]>('/analytics/token-usage', {
      params: { days, includeGateway },
    });
  },

  getModelUsage() {
    return apiClient.get<ModelUsage[]>('/analytics/model-usage');
  },

  getTopSessions(limit = 20) {
    return apiClient.get<TopSessionUsage[]>('/analytics/top-sessions', {
      params: { limit },
    });
  },

  getSourceBreakdown() {
    return apiClient.get<AnalyticsSourceBreakdown>('/analytics/source-breakdown');
  },
};
