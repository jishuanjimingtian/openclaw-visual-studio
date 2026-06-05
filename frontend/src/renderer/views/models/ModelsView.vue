<template>
  <div class="page models-page">
    <HeaderToolbar>
      <n-text v-if="lastRefreshedLabel" depth="3" class="refresh-hint">{{ lastRefreshedLabel }}</n-text>
      <n-button quaternary size="small" :loading="refreshing" @click="refreshAll">
        <template #icon><n-icon><refresh-outline /></n-icon></template>
        刷新
      </n-button>
      <n-button quaternary size="small" :loading="syncing" @click="syncFromOpenClaw">
        导入 OpenClaw
      </n-button>
      <n-button type="primary" size="small" @click="openCustomAdd">
        <template #icon><n-icon><add-outline /></n-icon></template>
        添加模型
      </n-button>
    </HeaderToolbar>

    <div class="page-body models-shell">
      <OpenClawOverviewPanel
        v-if="store.openclawOverview"
        :overview="store.openclawOverview"
        :config-exists="openclawConfigExists"
        :syncing="syncing"
        @sync="syncFromOpenClaw"
      />

      <section class="models-workspace">
        <n-tabs v-model:value="activeTab" type="line" animated class="models-tabs">
          <n-tab-pane name="market" tab="模型市场">
            <div class="models-tab-body">
              <div class="models-panel-toolbar">
                <div class="models-toolbar-filters">
                  <n-input
                    v-model:value="marketSearch"
                    placeholder="搜索模型名称、ID 或标签…"
                    clearable
                    style="width: min(260px, 100%)"
                    @keyup.enter="loadMarket"
                  />
                  <n-select
                    v-model:value="marketProvider"
                    placeholder="Provider"
                    :options="marketProviderOptions"
                    clearable
                    style="width: 140px"
                  />
                  <n-button type="primary" :loading="marketLoading" @click="loadMarket">
                    搜索
                  </n-button>
                </div>
                <n-button @click="openCustomAdd">
                  <template #icon><n-icon><add-outline /></n-icon></template>
                  自定义添加
                </n-button>
              </div>

              <div class="models-market-layout">
                <aside class="models-category-nav">
                  <n-menu
                    v-model:value="marketCategory"
                    :options="categoryMenuOptions"
                    @update:value="loadMarket"
                  />
                </aside>

                <main class="models-market-main models-tab-scroll">
                  <n-text v-if="marketOverview" depth="3" class="models-category-hint">
                    {{ activeCategoryDescription }}
                  </n-text>

                  <div v-if="marketLoading && marketModels.length === 0" class="models-grid">
                    <n-card v-for="i in 8" :key="i" size="small">
                      <n-skeleton text style="width: 55%" />
                      <n-skeleton text :repeat="3" />
                    </n-card>
                  </div>

                  <EmptyState
                    v-else-if="!marketLoading && marketModels.length === 0"
                    description="该分类下暂无模型，可尝试更换分类或关键词。"
                  />

                  <div v-else class="models-grid">
                    <ModelMarketCard
                      v-for="entry in marketModels"
                      :key="entry.modelRef"
                      :entry="entry"
                      @configure="configureFromMarket"
                      @test="testModel"
                    />
                  </div>
                </main>
              </div>
            </div>
          </n-tab-pane>

          <n-tab-pane name="mine" tab="我的模型">
            <div class="models-tab-body">
              <div class="models-panel-toolbar">
                <div class="models-toolbar-filters">
                  <n-input
                    v-model:value="searchQuery"
                    placeholder="搜索名称或模型 ID…"
                    clearable
                    style="width: min(220px, 100%)"
                  />
                  <n-select
                    v-model:value="providerFilter"
                    placeholder="Provider"
                    :options="providerFilterOptions"
                    clearable
                    style="width: 130px"
                  />
                  <n-select
                    v-model:value="statusFilter"
                    placeholder="状态"
                    :options="statusOptions"
                    clearable
                    style="width: 100px"
                  />
                  <n-select
                    v-model:value="syncFilter"
                    placeholder="OpenClaw"
                    :options="syncFilterOptions"
                    clearable
                    style="width: 120px"
                  />
                  <n-tag v-if="filteredModels.length" round size="small" :bordered="false">
                    {{ filteredModels.length }} 项
                  </n-tag>
                </div>
                <n-button type="primary" @click="openCustomAdd">
                  <template #icon><n-icon><add-outline /></n-icon></template>
                  添加模型
                </n-button>
              </div>

              <div class="models-tab-scroll">
                <div v-if="store.loading && store.models.length === 0" class="models-grid">
                  <n-card v-for="i in 6" :key="i" size="small">
                    <n-skeleton text style="width: 60%" />
                    <n-skeleton text :repeat="2" />
                  </n-card>
                </div>

                <EmptyState
                  v-else-if="!store.loading && store.models.length === 0"
                  description="暂无已配置模型。前往「模型市场」选择模型并自定义配置。"
                />

                <div v-else class="models-grid">
                  <ModelMineCard
                    v-for="model in filteredModels"
                    :key="model.id"
                    :model="model"
                    :testing="testingId === model.id"
                    :applying="applyingId === model.id"
                    @toggle="toggleModel"
                    @test="testModel"
                    @edit="openEdit"
                    @delete="confirmDelete"
                    @set-primary="openSetPrimary"
                    @sync-openclaw="quickApply"
                  />
                </div>
              </div>

              <div v-if="store.totalPages > 1" class="models-pagination">
                <n-pagination
                  v-model:page="page"
                  :page-count="store.totalPages"
                  :page-size="pageSize"
                  @update:page="onPageChange"
                />
              </div>
            </div>
          </n-tab-pane>
        </n-tabs>
      </section>
    </div>

    <ModelEditorModal
      v-model:show="editorVisible"
      :form="form"
      :editing="Boolean(editingModel)"
      :editing-model="editingModel"
      :saving="saving"
      :catalog-options="catalogOptions"
      :endpoint-options="endpointOptions"
      :fallback-options="fallbackOptions"
      :market-entry="marketEntry"
      @save="saveModel"
      @provider-change="onProviderChange"
    />

    <n-modal v-model:show="primaryDialogVisible">
      <n-card title="设为 OpenClaw 默认" style="width: 480px" :bordered="false" size="small">
        <n-text depth="3" style="display: block; margin-bottom: 12px">
          主模型：{{ primaryTargetRef }}
        </n-text>
        <n-form-item label="备用模型（按优先级）" :show-feedback="false">
          <n-select
            v-model:value="primaryFallbacks"
            multiple
            filterable
            tag
            :options="fallbackOptions"
            placeholder="主模型不可用时依次尝试"
          />
        </n-form-item>
        <template #footer>
          <n-space justify="end">
            <n-button @click="primaryDialogVisible = false">取消</n-button>
            <n-button type="primary" :loading="settingPrimary" @click="confirmSetPrimary">
              确定
            </n-button>
          </n-space>
        </template>
      </n-card>
    </n-modal>

    <ConfirmDialog
      v-model:visible="deleteVisible"
      title="删除模型"
      :content="`确定删除「${deleteTarget?.name}」？仅移除本平台记录，不会修改 OpenClaw 配置文件。`"
      type="danger"
      @confirm="doDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NInput, NButton, NTag, NSpace, NCard, NModal, NFormItem, NSelect,
  NSwitch, NSkeleton, NText, NPagination, NTabs, NTabPane, NMenu, NIcon,
} from 'naive-ui';
import { AddOutline, RefreshOutline } from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import EmptyState from '@/components/EmptyState.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import OpenClawOverviewPanel from './components/OpenClawOverviewPanel.vue';
import ModelEditorModal from './components/ModelEditorModal.vue';
import ModelMarketCard from './components/ModelMarketCard.vue';
import ModelMineCard from './components/ModelMineCard.vue';
import { useModelStore } from '@/stores/model';
import { modelApi } from '@/api/model';
import type { ModelConfig, ModelProvider, OpenClawCatalogModel } from '@shared/types';
import {
  PROVIDER_OPTIONS,
  MARKET_PROVIDER_OPTIONS,
  buildSavePayload,
  defaultBaseUrlForProvider,
  defaultFormState,
  formStateFromMarketEntry,
  formStateFromModel,
  isOpenClawModel,
  isOpenClawProvider,
  resolveModelRef,
  useFallbackOptions,
  type ModelFormState,
} from '@/composables/useOpenClawModel';

const message = useMessage();
const store = useModelStore();

const activeTab = ref<'market' | 'mine'>('market');

const marketSearch = ref('');
const marketProvider = ref<ModelProvider | ''>('');
const marketCategory = ref('all');
const marketLoading = ref(false);
const marketOverview = ref<Awaited<ReturnType<typeof modelApi.getModelMarket>>['data'] | null>(null);
const marketEntry = ref<OpenClawCatalogModel | null>(null);

const searchQuery = ref('');
const providerFilter = ref<ModelProvider | ''>('');
const statusFilter = ref<'enabled' | 'disabled' | ''>('');
const syncFilter = ref<'registered' | 'primary' | 'local-only' | ''>('');
const syncing = ref(false);
const refreshing = ref(false);
const saving = ref(false);
const testingId = ref<string | null>(null);
const applyingId = ref<string | null>(null);
const lastRefreshedAt = ref<Date | null>(null);

const page = ref(1);
const pageSize = 20;

const editorVisible = ref(false);
const editingModel = ref<ModelConfig | null>(null);
const form = ref<ModelFormState>(defaultFormState(null));
const catalogOptions = ref<{ label: string; value: string }[]>([]);
const endpointOptions = ref<{ label: string; value: string }[]>([]);

const primaryDialogVisible = ref(false);
const primaryTarget = ref<ModelConfig | null>(null);
const primaryFallbacks = ref<string[]>([]);
const settingPrimary = ref(false);

const deleteVisible = ref(false);
const deleteTarget = ref<ModelConfig | null>(null);

const providerFilterOptions = PROVIDER_OPTIONS;
const marketProviderOptions = MARKET_PROVIDER_OPTIONS;
const statusOptions = [
  { label: '已启用', value: 'enabled' },
  { label: '已禁用', value: 'disabled' },
];
const syncFilterOptions = [
  { label: '已同步 OpenClaw', value: 'registered' },
  { label: 'OpenClaw 默认', value: 'primary' },
  { label: '仅本地', value: 'local-only' },
];

const openclawConfigExists = computed(
  () => (store.openclawOverview?.models.length ?? 0) > 0
    || Boolean(store.openclawOverview?.primaryModelRef),
);

const lastRefreshedLabel = computed(() => {
  if (!lastRefreshedAt.value) return '';
  return `${lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })} 已刷新`;
});

const categoryMenuOptions = computed(() =>
  (marketOverview.value?.categories ?? [{ id: 'all', label: '全部' }]).map((c) => ({
    label: c.label,
    key: c.id,
  })),
);

const activeCategoryDescription = computed(() => {
  const cat = marketOverview.value?.categories?.find((c) => c.id === marketCategory.value);
  return cat?.description ?? '';
});

const marketModels = computed(() => marketOverview.value?.models ?? []);

const fallbackOptions = useFallbackOptions(
  computed(() => store.models),
  catalogOptions,
  computed(() => store.openclawOverview),
);

const primaryTargetRef = computed(() => {
  const m = primaryTarget.value;
  if (!m) return '';
  return resolveModelRef(m) ?? m.endpoint;
});

const filteredModels = computed(() => {
  const q = searchQuery.value.toLowerCase();
  return store.models.filter((model) => {
    const ref = resolveModelRef(model);
    const matchesSearch = !q
      || model.name.toLowerCase().includes(q)
      || model.endpoint.toLowerCase().includes(q)
      || (ref?.toLowerCase().includes(q) ?? false);
    const matchesProvider = !providerFilter.value || model.provider === providerFilter.value;
    const matchesStatus = !statusFilter.value
      || (statusFilter.value === 'enabled' && model.enabled)
      || (statusFilter.value === 'disabled' && !model.enabled);
    const matchesSync = !syncFilter.value
      || (syncFilter.value === 'registered' && model.registeredInOpenClaw)
      || (syncFilter.value === 'primary' && model.openclawPrimary)
      || (syncFilter.value === 'local-only' && isOpenClawModel(model) && !model.registeredInOpenClaw);
    return matchesSearch && matchesProvider && matchesStatus && matchesSync;
  });
});

onMounted(async () => {
  await refreshAll();
});

async function refreshAll() {
  refreshing.value = true;
  try {
    await Promise.all([
      store.fetchModels(page.value, pageSize),
      store.fetchOpenClawOverview(),
      loadMarket(),
    ]);
    lastRefreshedAt.value = new Date();
  } finally {
    refreshing.value = false;
  }
}

async function onPageChange(p: number) {
  page.value = p;
  await store.fetchModels(p, pageSize);
}

async function loadMarket() {
  marketLoading.value = true;
  try {
    const res = await modelApi.getModelMarket({
      category: marketCategory.value === 'all' ? undefined : marketCategory.value,
      provider: marketProvider.value || undefined,
      q: marketSearch.value.trim() || undefined,
    });
    marketOverview.value = res.data;
  } catch {
    message.error('加载模型市场失败');
    marketOverview.value = null;
  } finally {
    marketLoading.value = false;
  }
}

async function loadCatalog(provider: string) {
  form.value.openclawBaseUrl = defaultBaseUrlForProvider(
    provider as ModelProvider,
    store.openclawOverview,
  );

  if (isOpenClawProvider(provider)) {
    try {
      const res = await modelApi.getOpenClawCatalog(provider);
      catalogOptions.value = res.data.map((m) => ({
        label: `${m.displayName} (${m.modelRef})`,
        value: m.modelRef,
      }));
    } catch {
      catalogOptions.value = [];
    }
  } else {
    catalogOptions.value = [];
  }

  await loadEndpointOptions(provider);
}

async function loadEndpointOptions(provider: string) {
  if (!isOpenClawProvider(provider) || provider === 'custom' || provider === 'openclaw') {
    endpointOptions.value = [];
    return;
  }
  try {
    const res = await modelApi.getProviderEndpoints(provider);
    endpointOptions.value = res.data.map((e) => ({
      label: e.label,
      value: e.baseUrl,
    }));
    if (endpointOptions.value.length > 0 && !form.value.openclawBaseUrl) {
      form.value.openclawBaseUrl = endpointOptions.value[0].value;
    }
  } catch {
    endpointOptions.value = [];
  }
}

async function onProviderChange(provider: string) {
  await loadCatalog(provider);
}

async function syncFromOpenClaw() {
  syncing.value = true;
  try {
    await store.syncFromOpenClaw();
    message.success('已从 OpenClaw 导入模型');
    page.value = 1;
    await store.fetchModels(1, pageSize);
    await loadMarket();
    lastRefreshedAt.value = new Date();
  } catch {
    message.error('导入失败，请确认后端已启动且 OpenClaw 配置文件存在');
  } finally {
    syncing.value = false;
  }
}

async function toggleModel(model: ModelConfig, enabled: boolean) {
  try {
    await store.updateModel(model.id, {
      name: model.name,
      provider: model.provider,
      endpoint: model.endpoint,
      enabled,
    });
    model.enabled = enabled;
    await store.fetchOpenClawOverview();
    await loadMarket();
    message.success(enabled ? '已启用并同步 OpenClaw' : '已禁用并同步 OpenClaw');
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '操作失败';
    message.error(msg);
  }
}

function openCustomAdd() {
  marketEntry.value = null;
  editingModel.value = null;
  form.value = defaultFormState(store.openclawOverview);
  void loadCatalog(form.value.provider);
  editorVisible.value = true;
}

async function configureFromMarket(entry: OpenClawCatalogModel) {
  marketEntry.value = entry;
  if (entry.configured && entry.configuredModelId) {
    const model = store.models.find((m) => m.id === entry.configuredModelId)
      ?? (await store.fetchModel(entry.configuredModelId));
    editingModel.value = model;
    form.value = formStateFromModel(model, store.openclawOverview);
  } else {
    editingModel.value = null;
    form.value = formStateFromMarketEntry(entry, store.openclawOverview);
  }
  await loadCatalog(form.value.provider);
  editorVisible.value = true;
}

function openEdit(model: ModelConfig) {
  marketEntry.value = null;
  editingModel.value = model;
  form.value = formStateFromModel(model, store.openclawOverview);
  void loadCatalog(model.provider);
  editorVisible.value = true;
}

function openSetPrimary(model: ModelConfig) {
  primaryTarget.value = model;
  const ref = resolveModelRef(model);
  const existing = store.openclawOverview?.fallbackModelRefs ?? [];
  primaryFallbacks.value = existing.filter((fb) => fb !== ref);
  primaryDialogVisible.value = true;
}

async function confirmSetPrimary() {
  const model = primaryTarget.value;
  const modelRef = model ? resolveModelRef(model) : null;
  if (!modelRef) return;
  settingPrimary.value = true;
  try {
    await store.setOpenClawPrimary({ modelRef, fallbackModelRefs: primaryFallbacks.value });
    message.success(`已将默认模型设为 ${modelRef}`);
    primaryDialogVisible.value = false;
    await store.fetchModels(page.value, pageSize);
    await loadMarket();
  } catch {
    message.error('设置失败');
  } finally {
    settingPrimary.value = false;
  }
}

async function quickApply(model: ModelConfig) {
  applyingId.value = model.id;
  try {
    await store.applyToOpenClaw(model.id, true, false, true);
    message.success('已同步到 OpenClaw');
    await store.fetchModels(page.value, pageSize);
    await loadMarket();
  } catch {
    message.error('同步失败');
  } finally {
    applyingId.value = null;
  }
}

async function testModel(id: string) {
  testingId.value = id;
  try {
    const result = await store.testModel(id);
    const latency = result.latencyMs != null ? `（${result.latencyMs}ms）` : '';
    if (result.success) {
      message.success(`${result.message}${latency}`);
    } else {
      message.warning(result.message);
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '测试请求失败';
    message.error(msg);
  } finally {
    testingId.value = null;
  }
}

async function saveModel() {
  const openclaw = isOpenClawProvider(form.value.provider);
  if (!form.value.name.trim()) {
    message.warning('请填写显示名称');
    return;
  }
  const modelRef = (form.value.modelRef ?? '').trim();
  if (openclaw && !modelRef) {
    message.warning('请选择或输入模型 ID');
    return;
  }
  if (!openclaw && !form.value.endpoint.trim()) {
    message.warning('请填写 API Endpoint');
    return;
  }

  const payload = buildSavePayload(form.value);
  saving.value = true;
  try {
    if (editingModel.value) {
      await store.updateModel(editingModel.value.id, payload);
      message.success(payload.apiKey ? '已更新模型与 API Key' : '保存成功');
    } else {
      if (!payload.apiKey && !openclaw) {
        message.warning('建议填写 API Key');
      }
      await store.createModel(payload);
      message.success('配置已保存');
    }
    await store.fetchOpenClawOverview();
    await store.fetchModels(page.value, pageSize);
    await loadMarket();
    editorVisible.value = false;
    editingModel.value = null;
    marketEntry.value = null;
    activeTab.value = 'mine';
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '保存失败';
    if (msg.includes('已写入本地数据库')) {
      message.warning(msg);
      await store.fetchOpenClawOverview();
      await store.fetchModels(page.value, pageSize);
      await loadMarket();
      editorVisible.value = false;
    } else {
      message.error(msg);
    }
  } finally {
    saving.value = false;
  }
}

function confirmDelete(model: ModelConfig) {
  deleteTarget.value = model;
  deleteVisible.value = true;
}

async function doDelete() {
  if (!deleteTarget.value) return;
  try {
    await store.deleteModel(deleteTarget.value.id);
    message.success('已删除');
    await loadMarket();
  } catch {
    message.error('删除失败');
  } finally {
    deleteVisible.value = false;
    deleteTarget.value = null;
  }
}
</script>

<style scoped lang="scss">
@use './models.scss';
@use './models-market.scss';
</style>
