<template>
  <n-modal :show="show" @update:show="$emit('update:show', $event)">
    <n-card
      :title="editing ? '编辑模型配置' : '自定义模型配置'"
      style="width: 600px; max-height: 90vh; overflow: auto"
      :bordered="false"
      size="small"
      role="dialog"
    >
      <n-alert
        v-if="marketEntry?.description"
        type="info"
        :show-icon="false"
        style="margin-bottom: 12px"
      >
        {{ marketEntry.description }}
      </n-alert>

      <n-form label-placement="top" size="small">
        <n-divider title-placement="left">基本信息</n-divider>
        <n-form-item label="显示名称" required>
          <n-input v-model:value="form.name" placeholder="例如：Qwen Plus" />
        </n-form-item>
        <n-form-item label="Provider" required>
          <n-select
            v-model:value="form.provider"
            :options="providerOptions"
            @update:value="onProviderChange"
          />
        </n-form-item>

        <template v-if="openclaw">
          <n-form-item label="模型 ID" required>
            <n-select
              v-model:value="form.modelRef"
              filterable
              tag
              :options="catalogOptions"
              placeholder="如 qwen/qwen3.5-plus"
            />
            <template #feedback>格式：provider/modelId，与 OpenClaw 运行时一致</template>
          </n-form-item>
          <n-form-item v-if="endpointOptions.length > 0" label="API 端点">
            <n-select
              v-model:value="form.openclawBaseUrl"
              filterable
              tag
              :options="endpointOptions"
              placeholder="选择或输入自定义端点"
            />
          </n-form-item>
        </template>
        <n-form-item v-else label="API Endpoint" required>
          <n-input v-model:value="form.endpoint" placeholder="https://api.openai.com/v1" />
        </n-form-item>

        <n-divider title-placement="left">API Key</n-divider>
        <n-form-item>
          <n-input
            v-model:value="form.apiKey"
            type="password"
            show-password-on="click"
            :placeholder="apiKeyPlaceholder"
          />
          <n-space v-if="showSavedKeyBadge" :size="8" style="margin-top: 8px">
            <n-tag v-if="editingModel?.apiKeyPreview" type="success" size="small">
              本地已保存 {{ editingModel.apiKeyPreview }}
            </n-tag>
            <n-tag v-else-if="editingModel?.apiKeyInOpenClaw" type="info" size="small">
              OpenClaw 中已有 Key
            </n-tag>
          </n-space>
          <template #feedback>{{ apiKeyHint }}</template>
        </n-form-item>

        <template v-if="openclaw">
          <n-divider title-placement="left">OpenClaw 同步</n-divider>
          <n-form-item>
            <n-radio-group v-model:value="form.syncMode" name="sync-mode">
              <n-space vertical>
                <n-radio
                  v-for="opt in syncModeOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  <n-text>{{ opt.label }}</n-text>
                  <n-text depth="3" style="font-size: 12px; display: block; margin-left: 22px">
                    {{ opt.hint }}
                  </n-text>
                </n-radio>
              </n-space>
            </n-radio-group>
          </n-form-item>
          <n-form-item
            v-if="form.syncMode === 'primary'"
            label="备用模型（按优先级）"
          >
            <n-select
              v-model:value="form.fallbackModelRefs"
              multiple
              filterable
              tag
              :options="fallbackOptions"
              placeholder="主模型不可用时依次尝试"
            />
          </n-form-item>
        </template>

        <n-form-item label="启用">
          <n-switch v-model:value="form.enabled" />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="$emit('update:show', false)">取消</n-button>
          <n-button type="primary" :loading="saving" @click="$emit('save')">保存</n-button>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import {
  NModal, NCard, NForm, NFormItem, NInput, NSelect, NSwitch, NButton, NSpace,
  NText, NTag, NDivider, NRadioGroup, NRadio, NAlert,
} from 'naive-ui';
import type { ModelConfig, OpenClawCatalogModel } from '@shared/types';
import {
  PROVIDER_OPTIONS,
  SYNC_MODE_OPTIONS,
  isOpenClawProvider,
  type ModelFormState,
} from '@/composables/useOpenClawModel';

const props = defineProps<{
  show: boolean;
  form: ModelFormState;
  editing: boolean;
  editingModel: ModelConfig | null;
  saving?: boolean;
  catalogOptions: { label: string; value: string }[];
  endpointOptions: { label: string; value: string }[];
  fallbackOptions: { label: string; value: string }[];
  marketEntry?: OpenClawCatalogModel | null;
}>();

const emit = defineEmits<{
  'update:show': [boolean];
  save: [];
  'provider-change': [string];
}>();

const providerOptions = PROVIDER_OPTIONS;
const syncModeOptions = SYNC_MODE_OPTIONS;

const openclaw = computed(() => isOpenClawProvider(props.form.provider));

const apiKeyPlaceholders: Record<string, string> = {
  qwen: '阿里云 DashScope API Key（sk-...）',
  deepseek: 'DeepSeek API Key（sk-...）',
  openai: 'OpenAI API Key（sk-...）',
  claude: 'Anthropic API Key（sk-ant-...）',
  google: 'Google AI API Key',
  openrouter: 'OpenRouter API Key（sk-or-...）',
  ollama: 'Ollama 通常无需 Key，留空即可',
};

const apiKeyPlaceholder = computed(() => {
  if (props.form.apiKey) {
    return apiKeyPlaceholders[props.form.provider] ?? '输入 API Key';
  }
  const m = props.editingModel;
  if (m?.apiKeyPreview) return `已保存 ${m.apiKeyPreview}，留空不修改`;
  if (m?.apiKeyInOpenClaw) return 'OpenClaw 已有 Key，输入可覆盖';
  return apiKeyPlaceholders[props.form.provider] ?? '输入 API Key';
});

const showSavedKeyBadge = computed(() => {
  if (props.form.apiKey) return false;
  const m = props.editingModel;
  return Boolean(m?.apiKeyPreview || m?.apiKeyInOpenClaw);
});

const apiKeyHint = computed(() => {
  if (props.editingModel?.apiKeyConfigured && !props.form.apiKey) {
    return '密钥仅存于本地数据库（脱敏显示）；留空表示不修改';
  }
  if (openclaw.value && (props.form.syncMode === 'sync' || props.form.syncMode === 'primary')) {
    return '保存时将把 API Key 同步到 OpenClaw 本地配置';
  }
  if (openclaw.value) {
    return '仅保存到本平台；选择「完整同步」或「设为默认」可写入 OpenClaw';
  }
  return '密钥加密存储在本平台';
});

function onProviderChange(provider: string) {
  emit('provider-change', provider);
}
</script>
