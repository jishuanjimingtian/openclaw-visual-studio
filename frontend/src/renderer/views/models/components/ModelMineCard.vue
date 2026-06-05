<template>
  <n-card
    :class="[
      'mine-card',
      { 'is-disabled': !model.enabled, 'is-primary': model.openclawPrimary },
    ]"
    size="small"
  >
    <template #header>
      <n-space justify="space-between" align="center">
        <span class="mine-card-name">{{ model.name }}</span>
        <n-switch
          :value="model.enabled"
          size="small"
          @update:value="(v: boolean) => $emit('toggle', model, v)"
        />
      </n-space>
    </template>

    <n-space vertical :size="10" class="mine-card-body">
      <n-space :size="6" wrap>
        <n-tag size="small">{{ model.provider }}</n-tag>
        <n-tag
          v-for="tag in syncTags"
          :key="tag.label"
          size="small"
          :type="tag.type"
        >
          {{ tag.label }}
        </n-tag>
      </n-space>

      <div class="mine-card-ref">
        <span class="mine-card-ref-k">{{ modelRef ? '模型 ID' : 'Endpoint' }}</span>
        <span class="mine-card-ref-v">{{ modelRef || model.endpoint }}</span>
      </div>

      <span class="mine-card-meta">API Key：{{ apiKeyLabel }}</span>
    </n-space>

    <template #footer>
      <n-space justify="space-between" align="center" style="width: 100%">
        <n-button size="small" :loading="testing" @click="$emit('test', model.id)">
          测试
        </n-button>
        <div class="mine-card-actions">
          <n-button
            v-if="showSyncButton"
            size="small"
            type="primary"
            ghost
            :loading="applying"
            @click="$emit('sync-openclaw', model)"
          >
            同步到 OpenClaw
          </n-button>
          <n-button
            v-if="showPrimaryButton"
            size="small"
            ghost
            @click="$emit('set-primary', model)"
          >
            设为默认
          </n-button>
          <n-button size="small" @click="$emit('edit', model)">编辑</n-button>
          <n-button size="small" type="error" quaternary @click="$emit('delete', model)">
            删除
          </n-button>
        </div>
      </n-space>
    </template>
  </n-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NCard, NButton, NTag, NSpace, NSwitch } from 'naive-ui';
import type { ModelConfig } from '@shared/types';
import {
  apiKeyStatusLabel,
  isOpenClawModel,
  openClawSyncStatusTags,
  resolveModelRef,
} from '@/composables/useOpenClawModel';

const props = defineProps<{
  model: ModelConfig;
  testing?: boolean;
  applying?: boolean;
}>();

defineEmits<{
  toggle: [model: ModelConfig, enabled: boolean];
  test: [id: string];
  edit: [model: ModelConfig];
  delete: [model: ModelConfig];
  'set-primary': [model: ModelConfig];
  'sync-openclaw': [model: ModelConfig];
}>();

const modelRef = computed(() => resolveModelRef(props.model));
const apiKeyLabel = computed(() => apiKeyStatusLabel(props.model));

const syncTags = computed(() =>
  openClawSyncStatusTags(props.model).filter(
    (t) => t.label !== '本地' || isOpenClawModel(props.model),
  ),
);

const showSyncButton = computed(
  () => isOpenClawModel(props.model) && !props.model.registeredInOpenClaw,
);

const showPrimaryButton = computed(
  () => isOpenClawModel(props.model) && !props.model.openclawPrimary,
);
</script>

<style scoped lang="scss">
@use '../models-cards.scss';
</style>
