import apiClient from './client';
import type {
  ModelConfig,
  ModelConfigSaveRequest,
  ModelMarketOverview,
  OpenClawCatalogModel,
  OpenClawEndpointOption,
  OpenClawModelOverview,
  PageRequest,
  ModelTestResult,
  PageResult,
  SetOpenClawPrimaryRequest,
} from '@shared/types';
export const modelApi = {
  getOpenClawOverview() {
    return apiClient.get<OpenClawModelOverview>('/models/openclaw/overview');
  },

  syncFromOpenClaw() {
    return apiClient.post<ModelConfig[]>('/models/openclaw/sync');
  },

  getOpenClawCatalog(provider: string) {
    return apiClient.get<OpenClawCatalogModel[]>('/models/openclaw/catalog', {
      params: { provider },
    });
  },

  getModelMarket(params?: { category?: string; provider?: string; q?: string }) {
    return apiClient.get<ModelMarketOverview>('/models/market', { params });
  },

  getProviderEndpoints(provider: string) {
    return apiClient.get<OpenClawEndpointOption[]>('/models/openclaw/provider-endpoints', {
      params: { provider },
    });
  },

  getQwenEndpoints() {
    return apiClient.get<OpenClawEndpointOption[]>('/models/openclaw/qwen-endpoints');
  },

  getDeepSeekEndpoints() {
    return apiClient.get<OpenClawEndpointOption[]>('/models/openclaw/deepseek-endpoints');
  },

  setOpenClawPrimary(body: SetOpenClawPrimaryRequest) {
    return apiClient.post<void>('/models/openclaw/primary', body);
  },

  applyToOpenClaw(id: string, register = true, setAsPrimary = false, syncApiKey = true) {
    return apiClient.post<void>(`/models/${id}/apply-openclaw`, undefined, {
      params: { register, setAsPrimary, syncApiKey },
    });
  },

  setApiKey(id: string, apiKey: string, syncToOpenClaw = true) {
    return apiClient.patch<ModelConfig>(`/models/${id}/api-key`, {
      apiKey,
      syncToOpenClaw,
    });
  },
  listModels(params: PageRequest) {
    const query: Record<string, string | number> = {
      page: params.page - 1,
      size: params.pageSize,
    };
    if (params.sortBy) {
      query.sort = `${params.sortBy},${params.sortOrder || 'desc'}`;
    }
    return apiClient.get<PageResult<ModelConfig>>('/models', { params: query });
  },

  getModel(id: string) {
    return apiClient.get<ModelConfig>(`/models/${id}`);
  },

  createModel(model: ModelConfigSaveRequest) {
    return apiClient.post<ModelConfig>('/models', model);
  },

  updateModel(id: string, model: ModelConfigSaveRequest) {
    return apiClient.put<ModelConfig>(`/models/${id}`, model);
  },

  deleteModel(id: string) {
    return apiClient.delete<void>(`/models/${id}`);
  },

  testModel(id: string) {
    return apiClient.post<ModelTestResult>(`/models/${id}/test`);
  },
};