<template>
  <div class="settings-panel">
    <section class="settings-section">
      <header class="settings-section-head">
        <h3 class="settings-section-title">全局运行时</h3>
        <span class="settings-section-meta">agents.defaults</span>
      </header>

      <n-form
        v-if="overview?.configExists"
        label-placement="top"
        size="small"
        class="settings-form"
        :disabled="savingRuntime"
      >
        <div class="settings-form-grid">
          <n-form-item label="温度">
            <n-input-number
              v-model:value="runtimeForm.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="1"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="最大 Token">
            <n-input-number
              v-model:value="runtimeForm.maxTokens"
              :min="256"
              :max="32768"
              :step="256"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="超时（秒）" class="span-2">
            <n-input-number
              v-model:value="runtimeForm.timeoutSeconds"
              :min="30"
              :max="3600"
              :step="30"
              style="width: 100%"
            />
          </n-form-item>
          <n-form-item label="主模型" class="span-2">
            <n-select
              v-model:value="runtimeForm.primaryModel"
              placeholder="选择主模型"
              clearable
              filterable
              tag
              :options="modelOptions"
            />
          </n-form-item>
          <n-form-item label="工作区路径" class="span-2">
            <n-input v-model:value="runtimeForm.workspace" placeholder="agents.defaults.workspace" />
          </n-form-item>
        </div>
      </n-form>
      <n-alert v-else type="info" :bordered="false" :show-icon="false">
        配置文件就绪后可编辑全局参数。
      </n-alert>

      <div class="settings-prompt-row">
        <span class="settings-label">Prompt 来源</span>
        <PromptStatusBadge v-if="overview" :role="overview" compact />
      </div>

      <n-button
        block
        type="primary"
        size="small"
        :loading="savingRuntime"
        :disabled="!overview?.configExists"
        @click="$emit('save-runtime')"
      >
        保存全局默认
      </n-button>
      <n-button block size="small" quaternary :disabled="!canEditDefaults" @click="$emit('edit-defaults')">
        编辑 Prompt
      </n-button>
    </section>

    <section class="settings-section settings-section--prompt">
      <header class="settings-section-head">
        <h3 class="settings-section-title">{{ promptSectionTitle }}</h3>
      </header>

      <template v-if="overview?.promptSource === 'bootstrap'">
        <p class="settings-copy">
          由工作区 <code>AGENTS.md</code> 提供行为说明；可同步到 <code>openclaw.json</code> 统一维护。
        </p>
        <blockquote v-if="overview.bootstrapPreview" class="settings-preview">
          {{ overview.bootstrapPreview }}
        </blockquote>
        <div class="settings-meta">
          <span>{{ overview.bootstrapLineCount }} 行</span>
          <span>·</span>
          <n-tooltip trigger="hover">
            <template #trigger>
              <code>{{ displayPath(overview.bootstrapFile) }}</code>
            </template>
            {{ overview.bootstrapFile }}
          </n-tooltip>
        </div>
        <n-button block type="primary" size="small" :loading="syncingBootstrap" @click="$emit('sync-bootstrap')">
          同步到 openclaw.json
        </n-button>
        <n-button block size="small" :loading="loadingPrompt" @click="$emit('view-bootstrap')">
          预览 AGENTS.md
        </n-button>
        <n-button block size="small" quaternary @click="$emit('edit-defaults')">
          手动编辑
        </n-button>
      </template>

      <template v-else-if="overview?.promptSource === 'none'">
        <p class="settings-copy">
          未检测到 <code>systemPrompt</code> 或 <code>AGENTS.md</code>，可初始化默认模板。
        </p>
        <n-button block type="primary" size="small" :loading="syncingBootstrap" @click="$emit('init-bootstrap')">
          初始化 AGENTS.md
        </n-button>
      </template>

      <template v-else-if="overview?.promptSource === 'config'">
        <n-alert type="success" :bordered="false" :show-icon="false">
          已在配置文件中设置（{{ overview.systemPrompt?.length ?? 0 }} 字符）
        </n-alert>
        <n-button block size="small" @click="$emit('view-config-prompt')">查看全文</n-button>
        <n-button block size="small" quaternary type="warning" @click="$emit('clear-prompt')">
          清除并恢复 Bootstrap
        </n-button>
      </template>

      <n-alert v-else type="default" :bordered="false" :show-icon="false">
        加载配置后显示 Prompt 管理选项。
      </n-alert>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  NAlert, NButton, NForm, NFormItem, NInput, NInputNumber, NSelect, NTooltip,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import type { AgentConfigOverview } from '@shared/types';
import PromptStatusBadge from './PromptStatusBadge.vue';

const props = defineProps<{
  overview: AgentConfigOverview | null;
  runtimeForm: {
    temperature: number;
    maxTokens: number;
    timeoutSeconds: number;
    workspace: string;
    primaryModel: string;
  };
  modelOptions: SelectOption[];
  savingRuntime: boolean;
  syncingBootstrap: boolean;
  loadingPrompt: boolean;
  canEditDefaults: boolean;
}>();

defineEmits<{
  'save-runtime': [];
  'edit-defaults': [];
  'sync-bootstrap': [];
  'init-bootstrap': [];
  'view-bootstrap': [];
  'view-config-prompt': [];
  'clear-prompt': [];
}>();

const promptSectionTitle = computed(() => {
  const src = props.overview?.promptSource;
  if (src === 'bootstrap') return '工作区 Bootstrap';
  if (src === 'config') return 'System Prompt';
  if (src === 'none') return 'Prompt 未配置';
  return 'Prompt';
});

function displayPath(path?: string | null): string {
  if (!path) return '未设置';
  let p = path.replace(/\\/g, '/');
  p = p.replace(/^[A-Za-z]:\/Users\/[^/]+/, '~');
  p = p.replace(/^\/Users\/[^/]+/, '~');
  const parts = p.split('/').filter(Boolean);
  if (parts.length <= 3) return p;
  return `~/${parts.slice(-2).join('/')}`;
}
</script>

<style scoped lang="scss">
@use '../config-settings-panel.scss';
</style>
