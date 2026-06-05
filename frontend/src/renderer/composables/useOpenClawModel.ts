import { computed, type Ref } from 'vue';
import type {
  ModelConfig,
  ModelConfigSaveRequest,
  ModelProvider,
  OpenClawCatalogModel,
  OpenClawModelOverview,
  OpenClawSyncMode,
} from '@shared/types';

/** Providers that use provider/modelId refs and can sync to OpenClaw. */
export const OPENCLAW_PROVIDERS: ModelProvider[] = [
  'qwen',
  'deepseek',
  'openclaw',
  'openai',
  'claude',
  'ollama',
  'google',
  'openrouter',
];

export const PROVIDER_OPTIONS = [
  { label: 'Qwen（OpenClaw 内置）', value: 'qwen' as ModelProvider },
  { label: 'DeepSeek（OpenClaw 内置）', value: 'deepseek' as ModelProvider },
  { label: 'OpenClaw 插件', value: 'openclaw' as ModelProvider },
  { label: 'OpenAI', value: 'openai' as ModelProvider },
  { label: 'Claude', value: 'claude' as ModelProvider },
  { label: 'Google Gemini', value: 'google' as ModelProvider },
  { label: 'OpenRouter', value: 'openrouter' as ModelProvider },
  { label: 'Ollama', value: 'ollama' as ModelProvider },
  { label: '自定义', value: 'custom' as ModelProvider },
];

export const MARKET_PROVIDER_OPTIONS = PROVIDER_OPTIONS.filter(
  (p) => p.value !== 'custom' && p.value !== 'openclaw',
);

export const SYNC_MODE_OPTIONS: { label: string; value: OpenClawSyncMode; hint: string }[] = [
  { label: '仅保存到本平台', value: 'local', hint: '不修改 OpenClaw 本地配置' },
  { label: '注册到 OpenClaw', value: 'register', hint: '加入可用模型列表，不改默认与 API Key' },
  { label: '完整同步', value: 'sync', hint: '注册模型并将 API Key 写入 OpenClaw' },
  { label: '设为默认模型', value: 'primary', hint: '注册、同步 Key，并设为主模型（可配置备用）' },
];

const TAG_LABELS: Record<string, string> = {
  chat: '对话',
  coding: '代码',
  reasoning: '推理',
  fast: '快速',
  premium: '旗舰',
};

export function tagLabel(tag: string) {
  return TAG_LABELS[tag] ?? tag;
}

export function isOpenClawProvider(provider: string) {
  return OPENCLAW_PROVIDERS.includes(provider as ModelProvider);
}

export function isOpenClawModel(model: ModelConfig) {
  return isOpenClawProvider(model.provider) || Boolean(model.modelRef);
}

export function resolveModelRef(model: ModelConfig): string | null {
  return model.modelRef || (isOpenClawProvider(model.provider) ? model.endpoint : null);
}

export function apiKeyStatusLabel(model: ModelConfig): string {
  if (model.apiKeyPreview) return `本地 ${model.apiKeyPreview}`;
  if (model.apiKeyInOpenClaw) return 'OpenClaw 已配置';
  return model.apiKeyConfigured ? '已配置' : '未配置';
}

export function inferSyncMode(model: ModelConfig | null): OpenClawSyncMode {
  if (!model) return 'sync';
  if (model.openclawPrimary) return 'primary';
  if (model.registeredInOpenClaw) return model.apiKeyConfigured ? 'sync' : 'register';
  return 'local';
}

export function syncModeToFlags(mode: OpenClawSyncMode) {
  return {
    registerInOpenClaw: mode === 'register' || mode === 'sync' || mode === 'primary',
    setAsOpenClawPrimary: mode === 'primary',
    syncApiKeyToOpenClaw: mode === 'sync' || mode === 'primary',
  };
}

export interface ModelFormState {
  name: string;
  provider: ModelProvider;
  endpoint: string;
  modelRef: string | null;
  openclawBaseUrl: string | null;
  apiKey: string;
  enabled: boolean;
  syncMode: OpenClawSyncMode;
  fallbackModelRefs: string[];
}

export function defaultBaseUrlForProvider(
  provider: ModelProvider,
  overview: OpenClawModelOverview | null,
): string | null {
  switch (provider) {
    case 'qwen':
      return overview?.qwenBaseUrl ?? 'https://coding.dashscope.aliyuncs.com/v1';
    case 'deepseek':
      return overview?.deepseekBaseUrl ?? 'https://api.deepseek.com';
    case 'openai':
      return 'https://api.openai.com/v1';
    case 'claude':
      return 'https://api.anthropic.com';
    case 'google':
      return 'https://generativelanguage.googleapis.com/v1beta';
    case 'ollama':
      return 'http://localhost:11434';
    case 'openrouter':
      return 'https://openrouter.ai/api/v1';
    default:
      return null;
  }
}

export function defaultFormState(overview: OpenClawModelOverview | null): ModelFormState {
  return {
    name: '',
    provider: 'qwen',
    endpoint: '',
    modelRef: null,
    openclawBaseUrl: defaultBaseUrlForProvider('qwen', overview),
    apiKey: '',
    enabled: true,
    syncMode: 'sync',
    fallbackModelRefs: overview?.fallbackModelRefs?.filter(Boolean) ?? [],
  };
}

export function formStateFromMarketEntry(
  entry: OpenClawCatalogModel,
  overview: OpenClawModelOverview | null,
): ModelFormState {
  const provider = (entry.uiProvider ?? entry.provider) as ModelProvider;
  return {
    name: entry.displayName,
    provider,
    endpoint: '',
    modelRef: entry.modelRef,
    openclawBaseUrl: entry.defaultBaseUrl ?? defaultBaseUrlForProvider(provider, overview),
    apiKey: '',
    enabled: true,
    syncMode: 'sync',
    fallbackModelRefs: overview?.fallbackModelRefs?.filter((fb) => fb !== entry.modelRef) ?? [],
  };
}

export function formStateFromModel(
  model: ModelConfig,
  overview: OpenClawModelOverview | null,
): ModelFormState {
  const modelRef = resolveModelRef(model);
  return {
    name: model.name,
    provider: model.provider,
    endpoint: isOpenClawProvider(model.provider) ? '' : model.endpoint,
    modelRef,
    openclawBaseUrl: defaultBaseUrlForProvider(model.provider, overview),
    apiKey: '',
    enabled: model.enabled,
    syncMode: inferSyncMode(model),
    fallbackModelRefs: overview?.fallbackModelRefs?.filter((fb) => fb !== modelRef) ?? [],
  };
}

export function buildSavePayload(form: ModelFormState): ModelConfigSaveRequest {
  const openclaw = isOpenClawProvider(form.provider);
  const modelRef = (form.modelRef ?? '').trim();
  const flags = openclaw ? syncModeToFlags(form.syncMode) : {
    registerInOpenClaw: false,
    setAsOpenClawPrimary: false,
    syncApiKeyToOpenClaw: false,
  };

  const payload: ModelConfigSaveRequest = {
    name: form.name.trim(),
    provider: form.provider,
    endpoint: openclaw ? modelRef : form.endpoint.trim(),
    enabled: form.enabled,
    ...flags,
    fallbackModelRefs:
      openclaw && flags.setAsOpenClawPrimary ? form.fallbackModelRefs : undefined,
  };

  if (openclaw && form.openclawBaseUrl) {
    payload.openclawBaseUrl = form.openclawBaseUrl;
  }
  if (form.apiKey.trim()) {
    payload.apiKey = form.apiKey.trim();
  }
  return payload;
}

export function useFallbackOptions(
  models: Ref<ModelConfig[]>,
  catalogOptions: Ref<{ label: string; value: string }[]>,
  overview: Ref<OpenClawModelOverview | null>,
) {
  return computed(() => {
    const fromStore = models.value
      .map((m) => resolveModelRef(m))
      .filter((ref): ref is string => Boolean(ref));
    const fromOverview = (overview.value?.models ?? []).map((m) => m.modelRef);
    const fromCatalog = catalogOptions.value.map((o) => o.value);
    const unique = [...new Set([...fromStore, ...fromOverview, ...fromCatalog])];
    return unique.map((ref) => ({ label: ref, value: ref }));
  });
}

export function openClawSyncStatusTags(model: ModelConfig) {
  const tags: { label: string; type: 'default' | 'info' | 'success' | 'warning' }[] = [];
  tags.push({ label: '本地', type: 'default' });
  if (model.registeredInOpenClaw) {
    tags.push({ label: 'OpenClaw', type: 'info' });
  }
  if (model.openclawPrimary) {
    tags.push({ label: '默认', type: 'success' });
  }
  if (!model.apiKeyConfigured) {
    tags.push({ label: '缺 Key', type: 'warning' });
  }
  return tags;
}
