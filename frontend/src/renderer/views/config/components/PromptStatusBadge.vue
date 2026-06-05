<template>
  <span :class="['prompt-badge', `prompt-badge--${source}`]">
    <span v-if="source === 'config'" class="prompt-badge-dot" />
    <n-icon v-else-if="source === 'bootstrap'" size="12" class="prompt-badge-icon">
      <document-text-outline />
    </n-icon>
    <span class="prompt-badge-text">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NIcon } from 'naive-ui';
import { DocumentTextOutline } from '@vicons/ionicons5';

export interface PromptLike {
  promptSource?: 'none' | 'config' | 'bootstrap';
  systemPrompt?: string;
  bootstrapFile?: string;
  bootstrapLineCount?: number;
  bootstrapPreview?: string;
}

const props = withDefaults(
  defineProps<{
    role: PromptLike;
    compact?: boolean;
  }>(),
  { compact: false },
);

const source = computed(() => props.role.promptSource ?? 'none');

const label = computed(() => {
  if (source.value === 'config') {
    const len = props.role.systemPrompt?.length ?? 0;
    return props.compact ? `配置文件 · ${len} 字` : `openclaw.json · ${len} 字符`;
  }
  if (source.value === 'bootstrap') {
    const lines = props.role.bootstrapLineCount ?? 0;
    if (props.compact) return `AGENTS.md · ${lines} 行`;
    return props.role.bootstrapPreview || `工作区 AGENTS.md · ${lines} 行`;
  }
  return '未配置 Prompt';
});
</script>

<style scoped lang="scss">
@use '../config-prompt-badge.scss';
</style>
