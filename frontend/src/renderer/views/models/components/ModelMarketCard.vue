<template>
  <n-card class="market-card" size="small" :class="{ configured: entry.configured }">
    <div class="market-header">
      <div class="market-title">
        <h3>{{ entry.displayName }}</h3>
        <n-tag size="small">{{ entry.uiProvider ?? entry.provider }}</n-tag>
        <n-tag v-if="entry.configured" type="success" size="small">已配置</n-tag>
      </div>
      <n-text v-if="entry.contextWindow" depth="3" class="context-hint">
        {{ formatContext(entry.contextWindow) }}
      </n-text>
    </div>

    <p class="market-desc">{{ entry.description || entry.modelRef }}</p>

    <n-space v-if="entry.tags?.length" :size="4" style="margin-bottom: 8px">
      <n-tag v-for="tag in entry.tags.slice(0, 4)" :key="tag" size="tiny" :bordered="false">
        {{ tagLabel(tag) }}
      </n-tag>
    </n-space>

    <n-text code class="model-ref">{{ entry.modelRef }}</n-text>

    <template #footer>
      <n-space justify="end" :size="6">
        <n-button size="small" @click="$emit('configure', entry)">
          {{ entry.configured ? '编辑配置' : '自定义配置' }}
        </n-button>
        <n-button
          v-if="entry.configured && entry.configuredModelId"
          size="small"
          type="primary"
          ghost
          @click="$emit('test', entry.configuredModelId)"
        >
          测试
        </n-button>
      </n-space>
    </template>
  </n-card>
</template>

<script setup lang="ts">
import { NCard, NButton, NTag, NSpace, NText } from 'naive-ui';
import type { OpenClawCatalogModel } from '@shared/types';
import { tagLabel } from '@/composables/useOpenClawModel';

defineProps<{
  entry: OpenClawCatalogModel;
}>();

defineEmits<{
  configure: [OpenClawCatalogModel];
  test: [string];
}>();

function formatContext(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M ctx`;
  if (n >= 1000) return `${Math.round(n / 1000)}k ctx`;
  return `${n} ctx`;
}
</script>

<style scoped lang="scss">
@use '../models-cards.scss';
</style>
