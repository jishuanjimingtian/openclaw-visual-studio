<template>
  <article
    class="agent-item"
    :class="{ 'agent-item--default': role.defaultAgent || role.id === '__defaults__' }"
    @click="$emit('edit', role)"
  >
    <div class="agent-item-leading">
      <div class="agent-item-avatar" :class="{ 'agent-item-avatar--default': role.defaultAgent || role.id === '__defaults__' }">
        {{ role.name.charAt(0) }}
      </div>
      <div class="agent-item-main">
        <div class="agent-item-title">
          <span class="agent-item-name">{{ role.name }}</span>
          <span class="agent-item-id">{{ role.id }}</span>
          <n-tag v-if="role.defaultAgent" size="tiny" round type="primary" :bordered="false">默认</n-tag>
          <n-tag v-else-if="role.id === '__defaults__'" size="tiny" round type="warning" :bordered="false">全局</n-tag>
          <n-tag v-else-if="role.builtin && role.id === 'main'" size="tiny" round :bordered="false">本地</n-tag>
        </div>
        <PromptStatusBadge :role="role" compact />
      </div>
    </div>

    <div class="agent-item-stats">
      <div class="agent-stat">
        <span class="agent-stat-k">温度</span>
        <span class="agent-stat-v">
          {{ role.temperature.toFixed(1) }}
          <em v-if="!role.temperatureFromConfig">默认</em>
        </span>
      </div>
      <div class="agent-stat">
        <span class="agent-stat-k">Token</span>
        <span class="agent-stat-v">
          {{ role.maxTokens }}
          <em v-if="!role.maxTokensFromConfig">默认</em>
        </span>
      </div>
      <div v-if="role.defaultModel" class="agent-stat agent-stat--model">
        <span class="agent-stat-k">模型</span>
        <span class="agent-stat-v mono">{{ modelShortName(role.defaultModel) }}</span>
      </div>
    </div>

    <div class="agent-item-actions" @click.stop>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="$emit('duplicate', role)">
            <template #icon><n-icon><copy-outline /></n-icon></template>
          </n-button>
        </template>
        复制为新 Agent
      </n-tooltip>
      <n-tooltip v-if="role.promptSource === 'config' && role.systemPrompt" trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="$emit('view-prompt', role)">
            <template #icon><n-icon><document-text-outline /></n-icon></template>
          </n-button>
        </template>
        查看 Prompt
      </n-tooltip>
      <n-button
        v-if="!role.builtin && !role.defaultAgent && role.id !== '__defaults__'"
        size="tiny"
        quaternary
        circle
        type="error"
        @click="$emit('delete', role)"
      >
        <template #icon><n-icon><trash-outline /></n-icon></template>
      </n-button>
      <n-icon class="agent-item-chevron" size="16"><chevron-forward-outline /></n-icon>
    </div>
  </article>
</template>

<script setup lang="ts">
import { NButton, NIcon, NTag, NTooltip } from 'naive-ui';
import {
  CopyOutline, TrashOutline, DocumentTextOutline, ChevronForwardOutline,
} from '@vicons/ionicons5';
import type { AgentRole } from '@shared/types';
import PromptStatusBadge from './PromptStatusBadge.vue';

defineProps<{ role: AgentRole }>();

defineEmits<{
  edit: [role: AgentRole];
  duplicate: [role: AgentRole];
  delete: [role: AgentRole];
  'view-prompt': [role: AgentRole];
}>();

function modelShortName(ref?: string | null): string {
  if (!ref) return '';
  const slash = ref.indexOf('/');
  return slash >= 0 ? ref.slice(slash + 1) : ref;
}
</script>

<style scoped lang="scss">
@use '../config-agent-item.scss';
</style>
