<template>
  <div class="page config-page">
    <HeaderToolbar>
      <n-text v-if="lastRefreshedLabel" depth="3" class="refresh-hint">{{ lastRefreshedLabel }}</n-text>
      <n-button quaternary size="small" :disabled="!overview?.configPath" @click="copyConfigPath">
        <template #icon><n-icon><copy-outline /></n-icon></template>
        复制路径
      </n-button>
      <n-button quaternary size="small" @click="router.push('/models')">模型配置</n-button>
      <n-button quaternary size="small" @click="router.push('/deployment')">部署</n-button>
      <n-button quaternary size="small" :loading="loading" @click="reload">
        <template #icon><n-icon><refresh-outline /></n-icon></template>
        刷新
      </n-button>
      <n-button type="primary" size="small" :disabled="!overview?.configExists" @click="openCreate">
        <template #icon><n-icon><add-outline /></n-icon></template>
        新建 Agent
      </n-button>
    </HeaderToolbar>

    <div class="page-body config-shell">
      <!-- 状态条 -->
      <header class="config-status">
        <div class="config-status-leading">
          <span
            class="config-status-dot"
            :class="overview?.configExists ? 'is-on' : 'is-off'"
          />
          <div class="config-status-text">
            <span class="config-status-title">OpenClaw 本地配置</span>
            <span class="config-status-sub">实时读取 openclaw.json</span>
          </div>
          <n-tag v-if="overview?.configExists" size="small" type="success" round :bordered="false">已连接</n-tag>
          <n-tag v-else size="small" type="warning" round :bordered="false">未找到</n-tag>
        </div>
        <n-tooltip v-if="overview?.configPath" trigger="hover">
          <template #trigger>
            <code class="config-status-path" @click.stop="copyConfigPath">
              <n-icon size="12"><document-outline /></n-icon>
              {{ displayPath(overview.configPath) }}
            </code>
          </template>
          {{ overview.configPath }}
        </n-tooltip>
      </header>

      <n-alert
        v-if="loadError"
        type="error"
        class="config-inline-alert"
        :bordered="false"
        closable
        @close="loadError = null"
      >
        {{ loadError }}
      </n-alert>

      <n-alert
        v-else-if="overview && !overview.configExists"
        type="warning"
        class="config-inline-alert"
        :bordered="false"
      >
        未检测到配置文件，请先在「OpenClaw 部署」完成安装。
        <template #footer>
          <n-button size="small" @click="router.push('/deployment')">前往部署</n-button>
        </template>
      </n-alert>

      <section v-if="overview?.configExists && metricItems.length" class="config-metrics">
        <div
          v-for="m in metricItems"
          :key="m.label"
          :class="['config-metric', `config-metric--${m.accent}`]"
        >
          <div class="config-metric-icon">
            <n-icon :size="17"><component :is="m.icon" /></n-icon>
          </div>
          <div class="config-metric-body">
            <span class="config-metric-label">{{ m.label }}</span>
            <n-tooltip v-if="m.tooltip" trigger="hover">
              <template #trigger>
                <span class="config-metric-value">{{ m.value }}</span>
              </template>
              {{ m.tooltip }}
            </n-tooltip>
            <span v-else class="config-metric-value">{{ m.value }}</span>
          </div>
        </div>
      </section>

      <div v-if="overview?.fallbackModelRefs?.length" class="config-fallbacks">
        <span class="config-fallbacks-label">备用模型</span>
        <div class="config-chip-row">
          <span v-for="fb in overview.fallbackModelRefs" :key="fb" class="config-chip">
            {{ modelShortName(fb) }}
          </span>
        </div>
      </div>

      <!-- 双栏工作区 -->
      <div class="config-workspace">
        <section class="config-panel config-panel--agents">
          <div class="config-panel-head">
            <div>
              <h2 class="config-panel-title">
                <n-icon size="18" class="config-panel-title-icon"><people-outline /></n-icon>
                Agent 角色
              </h2>
              <p class="config-panel-desc">点击条目编辑 · 参数继承 agents.defaults</p>
            </div>
            <n-tag round size="small" :bordered="false" class="config-panel-badge">
              {{ displayRoles.length }}
            </n-tag>
          </div>

          <div class="config-panel-toolbar">
            <n-input
              v-model:value="agentSearch"
              size="small"
              placeholder="搜索名称 / 模型 / ID…"
              clearable
            >
              <template #prefix>
                <n-icon size="14"><search-outline /></n-icon>
              </template>
            </n-input>
          </div>

          <div class="config-panel-body">
            <div v-if="loading" class="config-panel-loading">
              <n-spin size="medium" />
            </div>
            <div v-else-if="displayRoles.length === 0" class="config-panel-empty">
              <n-empty
                :description="agentSearch ? '无匹配的 Agent' : '未检测到 Agent，可新建或在 openclaw.json 配置 agents.list'"
              />
            </div>
            <div v-else class="config-agent-list">
              <ConfigAgentCard
                v-for="role in displayRoles"
                :key="role.id"
                :role="role"
                @edit="openEdit"
                @duplicate="duplicateRole"
                @delete="confirmDelete"
                @view-prompt="viewPrompt"
              />
            </div>
          </div>
        </section>

        <aside class="config-panel config-panel--settings">
          <div class="config-panel-head">
            <div>
              <h2 class="config-panel-title">
                <n-icon size="18" class="config-panel-title-icon"><options-outline /></n-icon>
                全局设置
              </h2>
              <p class="config-panel-desc">运行时参数与 Prompt</p>
            </div>
          </div>
          <ConfigSettingsPanel
            :overview="overview"
            :runtime-form="runtimeForm"
            :model-options="modelOptions"
            :saving-runtime="savingRuntime"
            :syncing-bootstrap="syncingBootstrap"
            :loading-prompt="loadingPrompt"
            :can-edit-defaults="Boolean(defaultsRole)"
            @save-runtime="saveRuntimeDefaults"
            @edit-defaults="openEditDefaults"
            @sync-bootstrap="syncBootstrap"
            @init-bootstrap="initBootstrap"
            @view-bootstrap="viewBootstrapContent"
            @view-config-prompt="viewConfigPrompt"
            @clear-prompt="confirmClearPrompt"
          />
        </aside>
      </div>

      <n-drawer v-model:show="promptDrawerOpen" :width="560" placement="right" class="config-prompt-drawer">
        <n-drawer-content :title="promptDrawerTitle" closable>
          <n-spin :show="loadingPrompt">
            <pre class="prompt-drawer-body">{{ promptDrawerContent || '（空）' }}</pre>
          </n-spin>
          <template #footer>
            <n-space justify="end">
              <n-button size="small" @click="copyPromptContent">复制内容</n-button>
              <n-button size="small" type="primary" @click="promptDrawerOpen = false">关闭</n-button>
            </n-space>
          </template>
        </n-drawer-content>
      </n-drawer>

      <!-- 编辑弹窗 -->
      <n-modal
        v-model:show="showModal"
        :title="editingRole ? `编辑 · ${editingRole.name}` : '新建 Agent'"
        preset="card"
        class="config-modal"
        style="width: 640px; max-width: 92vw"
        :mask-closable="false"
      >
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" size="medium">
          <n-form-item v-if="!editingRole" label="快速模板">
            <n-select
              v-model:value="selectedTemplate"
              placeholder="从模板快速填充…"
              clearable
              :options="templateOptions"
              @update:value="applyTemplate"
            />
          </n-form-item>
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="Agent 显示名称" />
          </n-form-item>

          <n-alert
            v-if="editingRole?.promptSource === 'bootstrap'"
            type="info"
            :bordered="false"
            style="margin-bottom: 12px"
          >
            当前来自 AGENTS.md（{{ editingRole.bootstrapLineCount }} 行）。填写后将写入 openclaw.json 并优先生效。
          </n-alert>

          <n-form-item label="System Prompt">
            <n-input
              v-model:value="form.systemPrompt"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 10 }"
              placeholder="写入 openclaw.json；留空则继续使用 AGENTS.md"
            />
          </n-form-item>

          <n-grid :cols="3" :x-gap="12" responsive="screen">
            <n-grid-item span="1 m:1 l:1">
              <n-form-item label="温度">
                <n-input-number
                  v-model:value="form.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  :precision="1"
                  style="width: 100%"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="1 m:1 l:1">
              <n-form-item label="最大 Token">
                <n-input-number
                  v-model:value="form.maxTokens"
                  :min="256"
                  :max="32768"
                  :step="256"
                  style="width: 100%"
                />
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="1 m:1 l:1">
              <n-form-item label="默认模型">
                <n-select
                  v-model:value="form.defaultModel"
                  placeholder="选择模型"
                  clearable
                  filterable
                  tag
                  :options="modelOptions"
                />
              </n-form-item>
            </n-grid-item>
          </n-grid>

          <n-form-item v-if="!isEditingDefaults" label="工作区">
            <n-input v-model:value="form.workspace" placeholder="独立工作区路径（可选）" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showModal = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
          </n-space>
        </template>
      </n-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import {
  NGrid, NGridItem, NButton, NIcon, NTag, NEmpty, NSpin, NText, NAlert,
  NModal, NForm, NFormItem, NInput, NInputNumber, NSpace, NTooltip, NSelect,
  NDrawer, NDrawerContent,
} from 'naive-ui';
import {
  AddOutline, RefreshOutline, DocumentOutline,
  PeopleOutline, OptionsOutline, HardwareChipOutline, TimerOutline,
  ConstructOutline, FolderOpenOutline, CopyOutline, SearchOutline,
} from '@vicons/ionicons5';
import type { FormInst, FormRules, SelectOption } from 'naive-ui';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import ConfigAgentCard from './components/ConfigAgentCard.vue';
import ConfigSettingsPanel from './components/ConfigSettingsPanel.vue';
import { configApi } from '@/api/config';
import { modelApi } from '@/api/model';
import type { AgentConfigOverview, AgentRole } from '@shared/types';

const router = useRouter();
const message = useMessage();
const dialog = useDialog();

const roles = ref<AgentRole[]>([]);
const overview = ref<AgentConfigOverview | null>(null);
const modelRefs = ref<string[]>([]);
const loading = ref(false);
const saving = ref(false);
const savingRuntime = ref(false);
const syncingBootstrap = ref(false);
const loadingPrompt = ref(false);
const loadError = ref<string | null>(null);
const lastRefreshedAt = ref<Date | null>(null);
const agentSearch = ref('');
const showModal = ref(false);
const editingRole = ref<AgentRole | null>(null);
const formRef = ref<FormInst | null>(null);
const selectedTemplate = ref<string | null>(null);
const promptDrawerOpen = ref(false);
const promptDrawerTitle = ref('Prompt 预览');
const promptDrawerContent = ref('');

const runtimeForm = ref({
  temperature: 0.7,
  maxTokens: 2048,
  timeoutSeconds: 600,
  workspace: '',
  primaryModel: '',
});

const form = ref({
  name: '',
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 2048,
  defaultModel: '',
  workspace: '',
});

const lastRefreshedLabel = computed(() => {
  if (!lastRefreshedAt.value) return '';
  return lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }) + ' 更新';
});

const defaultsRole = computed(() =>
  roles.value.find((r) => r.id === '__defaults__')
  ?? roles.value.find((r) => r.defaultAgent),
);

const displayRoles = computed(() => {
  // 仅在有其它 Agent 时隐藏「全局默认」汇总行；单独 defaults / 磁盘 main 须展示
  const withoutSyntheticGlobal = roles.value.filter(
    (r) => !(r.id === '__defaults__' && r.name === '全局默认'),
  );
  let list = withoutSyntheticGlobal.length > 0 ? withoutSyntheticGlobal : roles.value;
  const q = agentSearch.value.trim().toLowerCase();
  if (!q) return list;
  return list.filter((r) =>
    r.name.toLowerCase().includes(q)
    || r.id.toLowerCase().includes(q)
    || (r.defaultModel && r.defaultModel.toLowerCase().includes(q)),
  );
});

const isEditingDefaults = computed(() => editingRole.value?.id === '__defaults__');

const modelOptions = computed<SelectOption[]>(() =>
  modelRefs.value.map((ref) => ({ label: ref, value: ref }))
);

const metricItems = computed(() => {
  const o = overview.value;
  if (!o) return [];
  return [
    {
      label: '主模型',
      value: o.primaryModelRef ? modelShortName(o.primaryModelRef) : '未设置',
      tooltip: o.primaryModelRef ?? undefined,
      icon: HardwareChipOutline,
      accent: 'model',
    },
    {
      label: '超时',
      value: `${o.timeoutSeconds ?? 600} 秒`,
      icon: TimerOutline,
      accent: 'timeout',
    },
    {
      label: '工具',
      value: o.toolsProfile || '默认',
      icon: ConstructOutline,
      accent: 'tools',
    },
    {
      label: '工作区',
      value: displayPath(o.workspace),
      tooltip: o.workspace ?? undefined,
      icon: FolderOpenOutline,
      accent: 'workspace',
    },
  ];
});

const ROLE_TEMPLATES: Record<string, Omit<typeof form.value, 'workspace'>> = {
  general: {
    name: '通用助手',
    systemPrompt: '你是一个有帮助的通用 AI 助手。请用中文回答用户问题，简洁清晰。',
    temperature: 0.7,
    maxTokens: 2048,
    defaultModel: '',
  },
  code: {
    name: '代码专家',
    systemPrompt: '你是一个资深软件工程师。回答技术问题时，提供代码示例并解释关键概念。',
    temperature: 0.3,
    maxTokens: 4096,
    defaultModel: '',
  },
  translate: {
    name: '翻译助手',
    systemPrompt: '你是一个专业翻译。准确翻译用户提供的文本，保持原意和语气。',
    temperature: 0.2,
    maxTokens: 2048,
    defaultModel: '',
  },
  creative: {
    name: '创意写作',
    systemPrompt: '你是一个创意写作助手。发挥想象力创作故事、诗歌和文案，风格生动有趣。',
    temperature: 0.9,
    maxTokens: 4096,
    defaultModel: '',
  },
};

const templateOptions: SelectOption[] = [
  { label: '通用助手', value: 'general' },
  { label: '代码专家', value: 'code' },
  { label: '翻译助手', value: 'translate' },
  { label: '创意写作', value: 'creative' },
];

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
};

function displayPath(path?: string | null): string {
  if (!path) return '未设置';
  let p = path.replace(/\\/g, '/');
  p = p.replace(/^[A-Za-z]:\/Users\/[^/]+/, '~');
  p = p.replace(/^\/Users\/[^/]+/, '~');
  const parts = p.split('/').filter(Boolean);
  if (parts.length <= 3) return p;
  return `~/${parts.slice(-2).join('/')}`;
}

function modelShortName(ref?: string | null): string {
  if (!ref) return '';
  const slash = ref.indexOf('/');
  return slash >= 0 ? ref.slice(slash + 1) : ref;
}

function syncRuntimeFormFromOverview(o: AgentConfigOverview) {
  runtimeForm.value = {
    temperature: o.defaultTemperature ?? 0.7,
    maxTokens: o.defaultMaxTokens ?? 2048,
    timeoutSeconds: o.timeoutSeconds ?? 600,
    workspace: o.workspace ?? '',
    primaryModel: o.primaryModelRef ?? '',
  };
}

watch(overview, (o) => {
  if (o?.configExists) syncRuntimeFormFromOverview(o);
}, { immediate: true });

async function loadOverview() {
  try {
    const res = await configApi.getOverview();
    overview.value = res.data;
    if (res.data?.configExists) syncRuntimeFormFromOverview(res.data);
  } catch (e: unknown) {
    overview.value = null;
    loadError.value = e instanceof Error ? e.message : '加载配置概览失败';
  }
}

async function loadModels() {
  try {
    const res = await modelApi.getOpenClawOverview();
    modelRefs.value = res.data.models.map((m) => m.modelRef);
    if (res.data.primaryModelRef && !modelRefs.value.includes(res.data.primaryModelRef)) {
      modelRefs.value.unshift(res.data.primaryModelRef);
    }
  } catch {
    modelRefs.value = [];
  }
}

async function loadRoles() {
  try {
    const res = await configApi.listRoles();
    roles.value = res.data;
  } catch {
    message.error('加载 Agent 配置失败');
  }
}

async function reload() {
  loading.value = true;
  loadError.value = null;
  try {
    await Promise.all([loadOverview(), loadModels(), loadRoles()]);
    lastRefreshedAt.value = new Date();
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : '刷新失败';
  } finally {
    loading.value = false;
  }
}

async function copyConfigPath() {
  const path = overview.value?.configPath;
  if (!path) return;
  try {
    await navigator.clipboard.writeText(path);
    message.success('已复制配置路径');
  } catch {
    message.error('复制失败');
  }
}

function openEditDefaults() {
  if (defaultsRole.value) openEdit(defaultsRole.value);
}

async function saveRuntimeDefaults() {
  const role = defaultsRole.value;
  if (!role) {
    message.warning('未找到全局默认 Agent');
    return;
  }
  savingRuntime.value = true;
  try {
    await configApi.saveRole({
      id: role.id === '__defaults__' ? '__defaults__' : role.id,
      name: role.name,
      temperature: runtimeForm.value.temperature,
      maxTokens: runtimeForm.value.maxTokens,
      timeoutSeconds: runtimeForm.value.timeoutSeconds,
      defaultModel: runtimeForm.value.primaryModel,
      workspace: runtimeForm.value.workspace,
      systemPrompt: role.promptSource === 'config' ? role.systemPrompt : undefined,
    });
    message.success('全局默认已保存');
    await reload();
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    savingRuntime.value = false;
  }
}

function duplicateRole(role: AgentRole) {
  editingRole.value = null;
  selectedTemplate.value = null;
  form.value = {
    name: `${role.name} 副本`,
    systemPrompt: role.promptSource === 'config' ? role.systemPrompt : '',
    temperature: role.temperature,
    maxTokens: role.maxTokens,
    defaultModel: role.defaultModel,
    workspace: role.workspace ?? overview.value?.workspace ?? '',
  };
  showModal.value = true;
}

function viewPrompt(role: AgentRole) {
  promptDrawerTitle.value = `${role.name} · System Prompt`;
  promptDrawerContent.value = role.systemPrompt || '';
  promptDrawerOpen.value = true;
}

function viewConfigPrompt() {
  const text = overview.value?.systemPrompt ?? '';
  promptDrawerTitle.value = 'openclaw.json · System Prompt';
  promptDrawerContent.value = text;
  promptDrawerOpen.value = true;
}

async function viewBootstrapContent() {
  loadingPrompt.value = true;
  promptDrawerOpen.value = true;
  promptDrawerTitle.value = 'AGENTS.md';
  promptDrawerContent.value = '';
  try {
    const res = await configApi.getBootstrapContent();
    promptDrawerContent.value = res.data.content;
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '读取失败');
    promptDrawerOpen.value = false;
  } finally {
    loadingPrompt.value = false;
  }
}

async function copyPromptContent() {
  if (!promptDrawerContent.value) return;
  try {
    await navigator.clipboard.writeText(promptDrawerContent.value);
    message.success('已复制');
  } catch {
    message.error('复制失败');
  }
}

function confirmClearPrompt() {
  dialog.warning({
    title: '清除配置内 Prompt',
    content: '将移除 openclaw.json 中的 systemPrompt，OpenClaw 将恢复使用工作区 AGENTS.md（若存在）。',
    positiveText: '清除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const role = defaultsRole.value;
      if (!role) return;
      saving.value = true;
      try {
        await configApi.saveRole({
          id: role.id === '__defaults__' ? '__defaults__' : role.id,
          name: role.name,
          systemPrompt: '',
          temperature: role.temperature,
          maxTokens: role.maxTokens,
          defaultModel: role.defaultModel,
          workspace: role.workspace,
        });
        message.success('已清除，将使用 Bootstrap');
        await reload();
      } catch (e: unknown) {
        message.error(e instanceof Error ? e.message : '操作失败');
      } finally {
        saving.value = false;
      }
    },
  });
}

function applyTemplate(key: string | null) {
  if (!key || !ROLE_TEMPLATES[key]) return;
  form.value = { ...form.value, ...ROLE_TEMPLATES[key] };
}

function openCreate() {
  editingRole.value = null;
  selectedTemplate.value = null;
  form.value = {
    name: '',
    systemPrompt: '',
    temperature: overview.value?.defaultTemperature ?? 0.7,
    maxTokens: overview.value?.defaultMaxTokens ?? 2048,
    defaultModel: overview.value?.primaryModelRef ?? '',
    workspace: overview.value?.workspace ?? '',
  };
  showModal.value = true;
}

async function syncBootstrap() {
  syncingBootstrap.value = true;
  try {
    const defaultRole = roles.value.find((r) => r.defaultAgent || r.id === '__defaults__');
    const res = await configApi.syncBootstrapPrompt(defaultRole?.id);
    message.success(res.data.message || `已同步 ${res.data.charCount} 字符`);
    await reload();
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '同步失败');
  } finally {
    syncingBootstrap.value = false;
  }
}

async function initBootstrap() {
  syncingBootstrap.value = true;
  try {
    const res = await configApi.initWorkspaceBootstrap();
    message.success(res.data.message);
    await reload();
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '初始化失败');
  } finally {
    syncingBootstrap.value = false;
  }
}

function openEdit(role: AgentRole) {
  editingRole.value = role;
  selectedTemplate.value = null;
  form.value = {
    name: role.name,
    systemPrompt: role.promptSource === 'config' ? role.systemPrompt : '',
    temperature: role.temperature,
    maxTokens: role.maxTokens,
    defaultModel: role.defaultModel,
    workspace: role.workspace ?? '',
  };
  showModal.value = true;
}

async function handleSave() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  saving.value = true;
  try {
    await configApi.saveRole({
      id: editingRole.value?.id,
      name: form.value.name,
      systemPrompt: form.value.systemPrompt,
      temperature: form.value.temperature,
      maxTokens: form.value.maxTokens,
      defaultModel: form.value.defaultModel,
      workspace: form.value.workspace || undefined,
    });
    message.success('已保存');
    showModal.value = false;
    await reload();
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

function confirmDelete(role: AgentRole) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除 Agent「${role.name}」？将从 openclaw.json 的 agents.list 中移除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await configApi.deleteRole(role.id);
        message.success('已删除');
        await reload();
      } catch {
        message.error('删除失败');
      }
    },
  });
}

onMounted(() => reload());
</script>

<style scoped lang="scss">
@use './config.scss';
</style>
<style lang="scss">
@use './config-overlay.scss';
</style>
