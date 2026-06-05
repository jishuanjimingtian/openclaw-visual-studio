<template>
  <n-form label-placement="top" :show-feedback="!!errorText">
    <n-alert v-if="errorText" type="error" :show-icon="false" style="margin-bottom: 12px">
      {{ errorText }}
    </n-alert>

    <n-space v-if="showTemplates" :size="8" style="margin-bottom: 12px">
      <n-text depth="3">模板：</n-text>
      <n-button size="tiny" @click="applyTemplate('mainReminder')">主会话提醒</n-button>
      <n-button size="tiny" @click="applyTemplate('isolatedDaily')">隔离日报</n-button>
      <n-button size="tiny" @click="applyTemplate('webhookDigest')">Webhook 摘要</n-button>
    </n-space>

    <n-collapse :default-expanded-names="['basic', 'schedule', 'exec']">
      <n-collapse-item title="基础" name="basic">
        <n-form-item label="名称" required>
          <n-input v-model:value="model.name" placeholder="任务名称" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="model.description" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="model.enabled" />
        </n-form-item>
        <n-form-item label="Agent">
          <n-select
            v-model:value="model.agentId"
            :options="agentOptions"
            clearable
            placeholder="默认 main"
            :loading="agentsLoading"
          />
        </n-form-item>
        <n-form-item v-if="mode === 'edit'" label="清除 Agent 绑定">
          <n-switch v-model:value="model.clearAgent" />
        </n-form-item>
      </n-collapse-item>

      <n-collapse-item title="调度" name="schedule">
        <TimerScheduleFields v-model="model" />
      </n-collapse-item>

      <n-collapse-item title="执行" name="exec">
        <n-form-item label="会话模式">
          <n-radio-group v-model:value="model.sessionMode">
            <n-radio value="main">主会话</n-radio>
            <n-radio value="isolated">隔离</n-radio>
            <n-radio value="current">当前会话</n-radio>
            <n-radio value="custom">自定义 session:</n-radio>
          </n-radio-group>
        </n-form-item>
        <n-form-item v-if="model.sessionMode === 'custom'" label="Session ID">
          <n-input v-model:value="model.customSessionId" placeholder="daily-brief" />
        </n-form-item>

        <template v-if="model.sessionMode === 'main'">
          <n-form-item label="系统事件" required>
            <n-input v-model:value="model.systemEvent" type="textarea" :rows="3" />
          </n-form-item>
          <n-form-item label="唤醒">
            <n-radio-group v-model:value="model.wakeMode">
              <n-radio value="now">立即</n-radio>
              <n-radio value="next-heartbeat">下次心跳</n-radio>
            </n-radio-group>
          </n-form-item>
        </template>
        <template v-else>
          <n-form-item label="Agent 提示词" required>
            <n-input v-model:value="model.message" type="textarea" :rows="4" />
          </n-form-item>
          <n-form-item label="模型覆盖 (可选)">
            <n-select
              v-model:value="model.model"
              :options="modelOptions"
              clearable
              filterable
              tag
              placeholder="使用 Agent 默认"
              :loading="modelsLoading"
            />
          </n-form-item>
          <n-form-item label="思考级别">
            <n-select
              v-model:value="model.thinking"
              clearable
              :options="thinkingOptions"
              placeholder="默认"
            />
          </n-form-item>
          <n-form-item label="轻量上下文">
            <n-switch v-model:value="model.lightContext" />
          </n-form-item>
        </template>
      </n-collapse-item>

      <n-collapse-item title="投递" name="delivery">
        <TimerDeliveryFields v-model="model" :session-mode="model.sessionMode" />
      </n-collapse-item>

      <n-collapse-item title="高级" name="advanced">
        <n-form-item label="超时 (秒)">
          <n-input-number v-model:value="model.timeoutSeconds" :min="0" clearable style="width: 100%" />
        </n-form-item>
        <n-form-item label="失败告警包含 skipped">
          <n-switch v-model:value="model.includeSkippedAlerts" />
        </n-form-item>
      </n-collapse-item>
    </n-collapse>

    <n-space justify="end" style="margin-top: 16px">
      <n-button @click="emit('cancel')">取消</n-button>
      <n-button type="primary" :loading="saving" @click="submit">保存</n-button>
    </n-space>
  </n-form>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  NAlert, NButton, NCollapse, NCollapseItem, NForm, NFormItem, NInput, NInputNumber,
  NRadio, NRadioGroup, NSelect, NSpace, NSwitch, NText,
} from 'naive-ui';
import { configApi } from '@/api/config';
import { modelApi } from '@/api/model';
import TimerScheduleFields from './TimerScheduleFields.vue';
import TimerDeliveryFields from './TimerDeliveryFields.vue';
import {
  FORM_TEMPLATES,
  buildCreatePayload,
  buildPatch,
  defaultFormModel,
  formFromJob,
  validateForm,
  type TimerFormModel,
} from '../cronFormUtils';
import type { CronJob } from '@shared/types';

const props = defineProps<{
  mode: 'create' | 'edit';
  job?: CronJob | null;
  saving?: boolean;
  showTemplates?: boolean;
}>();

const emit = defineEmits<{
  cancel: [];
  submit: [payload: Record<string, unknown>, isCreate: boolean];
}>();

const model = ref<TimerFormModel>(defaultFormModel());
const errorText = ref('');
const agentsLoading = ref(false);
const modelsLoading = ref(false);
const agentOptions = ref<{ label: string; value: string }[]>([]);
const modelOptions = ref<{ label: string; value: string }[]>([]);

const thinkingOptions = [
  { label: 'off', value: 'off' },
  { label: 'low', value: 'low' },
  { label: 'medium', value: 'medium' },
  { label: 'high', value: 'high' },
];

onMounted(async () => {
  if (props.mode === 'edit' && props.job) {
    model.value = formFromJob(props.job);
  }
  agentsLoading.value = true;
  try {
    const roles = await configApi.listRoles();
    agentOptions.value = roles
      .filter((r) => r.id && r.id !== '__defaults__')
      .map((r) => ({ label: r.name || r.id!, value: r.id! }));
  } catch {
    agentOptions.value = [];
  } finally {
    agentsLoading.value = false;
  }

  modelsLoading.value = true;
  try {
    const overview = await modelApi.getOpenClawOverview();
    modelOptions.value = (overview?.models ?? []).map((m) => ({
      label: m.displayName ?? m.modelRef,
      value: m.modelRef,
    }));
    if (overview?.primaryModelRef) {
      modelOptions.value.unshift({
        label: `默认 ${overview.primaryModelRef}`,
        value: overview.primaryModelRef,
      });
    }
  } catch {
    modelOptions.value = [];
  } finally {
    modelsLoading.value = false;
  }
});

function applyTemplate(key: keyof typeof FORM_TEMPLATES) {
  model.value = FORM_TEMPLATES[key]();
}

function submit() {
  errorText.value = validateForm(model.value) ?? '';
  if (errorText.value) return;
  if (props.mode === 'create') {
    emit('submit', buildCreatePayload(model.value), true);
  } else if (props.job) {
    emit('submit', buildPatch(model.value, props.job), false);
  }
}
</script>

<style scoped>
:deep(.n-collapse-item__header) {
  font-weight: 600;
  color: var(--oc-timer-text);
}

:deep(.n-form-item-label) {
  color: var(--oc-timer-text-muted) !important;
}

:deep(.n-collapse-item__content-inner) {
  padding-top: 4px;
}
</style>
