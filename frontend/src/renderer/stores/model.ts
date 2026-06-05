import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { modelApi } from '@/api/model';
import type {
  ModelConfig,
  ModelConfigSaveRequest,
  ModelTestResult,
  OpenClawModelOverview,
  PageRequest,
  SetOpenClawPrimaryRequest,
} from '@shared/types';

export const useModelStore = defineStore('model', () => {
  const models = ref<ModelConfig[]>([]);
  const currentModel = ref<ModelConfig | null>(null);
  const loading = ref(false);
  const testing = ref(false);
  const testResult = ref<{ id: string; result: ModelTestResult } | null>(null);
  const totalElements = ref(0);
  const totalPages = ref(0);
  const currentPage = ref(1);
  const openclawOverview = ref<OpenClawModelOverview | null>(null);

  const enabledModels = computed(() => models.value.filter((m: ModelConfig) => m.enabled));

  async function fetchModels(page = 1, size = 20) {
    loading.value = true;
    try {
      const params: PageRequest = { page, pageSize: size, sortBy: 'updatedAt', sortOrder: 'desc' };
      const res = await modelApi.listModels(params);
      models.value = res.data.content;
      totalElements.value = res.data.totalElements;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.number + 1;
    } finally {
      loading.value = false;
    }
  }

  async function fetchModel(id: string) {
    loading.value = true;
    try {
      const res = await modelApi.getModel(id);
      currentModel.value = res.data;
      return res.data;
    } finally {
      loading.value = false;
    }
  }

  async function createModel(model: ModelConfigSaveRequest) {
    const res = await modelApi.createModel(model);
    models.value.unshift(res.data);
    return res.data;
  }

  async function updateModel(id: string, model: ModelConfigSaveRequest) {
    const res = await modelApi.updateModel(id, model);
    const idx = models.value.findIndex((m: ModelConfig) => m.id === id);
    if (idx !== -1) models.value[idx] = res.data;
    if (currentModel.value?.id === id) currentModel.value = res.data;
    return res.data;
  }

  async function deleteModel(id: string) {
    await modelApi.deleteModel(id);
    models.value = models.value.filter((m: ModelConfig) => m.id !== id);
    if (currentModel.value?.id === id) currentModel.value = null;
  }

  async function fetchOpenClawOverview() {
    const res = await modelApi.getOpenClawOverview();
    openclawOverview.value = res.data;
    return res.data;
  }

  async function syncFromOpenClaw() {
    loading.value = true;
    try {
      const res = await modelApi.syncFromOpenClaw();
      models.value = res.data;
      await fetchOpenClawOverview();
      return res.data;
    } finally {
      loading.value = false;
    }
  }

  async function setOpenClawPrimary(body: SetOpenClawPrimaryRequest) {
    await modelApi.setOpenClawPrimary(body);
    await fetchOpenClawOverview();
    await fetchModels(currentPage.value);
  }

  async function setApiKey(id: string, apiKey: string, syncToOpenClaw = true) {
    const res = await modelApi.setApiKey(id, apiKey, syncToOpenClaw);
    const idx = models.value.findIndex((m: ModelConfig) => m.id === id);
    if (idx !== -1) models.value[idx] = res.data;
    return res.data;
  }

  async function applyToOpenClaw(id: string, register = true, setAsPrimary = false, syncApiKey = true) {
    await modelApi.applyToOpenClaw(id, register, setAsPrimary, syncApiKey);
    await fetchOpenClawOverview();
    await fetchModels(currentPage.value);
  }

  async function testModel(id: string) {
    testing.value = true;
    testResult.value = null;
    try {
      const res = await modelApi.testModel(id);
      testResult.value = { id, result: res.data };
      return res.data;
    } finally {
      testing.value = false;
    }
  }

  return {
    models,
    currentModel,
    loading,
    testing,
    testResult,
    totalElements,
    totalPages,
    currentPage,
    enabledModels,
    openclawOverview,
    fetchOpenClawOverview,
    syncFromOpenClaw,
    setOpenClawPrimary,
    applyToOpenClaw,
    setApiKey,
    fetchModels,
    fetchModel,
    createModel,
    updateModel,
    deleteModel,
    testModel,
  };
});