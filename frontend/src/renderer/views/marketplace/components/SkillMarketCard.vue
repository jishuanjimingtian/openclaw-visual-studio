<template>
  <n-card class="skill-card" size="small" :class="{ 'skill-card--installing': installing }">
    <div class="skill-header">
      <div class="skill-title">
        <h3>{{ skill.name }}</h3>
        <n-tag size="small" :type="sourceType">{{ skill.source }}</n-tag>
        <n-tag v-if="skill.localized" size="small" type="warning">中文</n-tag>
        <n-tag v-if="isInstalled" type="success" size="small">已安装</n-tag>
        <n-tag v-else-if="skill.status === 'update_available'" type="warning" size="small">可更新</n-tag>
      </div>
      <div class="skill-stats">
        <n-rate readonly :value="skill.rating" size="small" allow-half />
        <span class="stat-text">{{ formatCount(skill.stars || skill.downloads) }}</span>
      </div>
    </div>

    <p class="skill-desc">{{ truncatedDesc }}</p>

    <div v-if="installing" class="install-progress">
      <n-progress type="line" :percentage="100" processing :show-indicator="false" />
      <span class="install-progress-text">{{ installProgressText }}</span>
    </div>

    <n-space v-if="skill.tags?.length" :size="4" style="margin-bottom: 8px">
      <n-tag v-for="tag in skill.tags.slice(0, 3)" :key="tag" size="tiny">{{ tag }}</n-tag>
    </n-space>

    <div class="skill-meta">
      <span v-if="skill.author">{{ skill.author }}</span>
      <span>v{{ skill.version }}</span>
    </div>

    <template #footer>
      <n-space justify="end" :size="6">
        <n-button size="small" :disabled="installing" @click="$emit('detail', skill)">详情</n-button>
        <n-button
          v-if="!installedView && isInstalled"
          size="small"
          type="success"
          disabled
        >
          已安装
        </n-button>
        <n-button
          v-else-if="!installedView && canInstall"
          size="small"
          type="primary"
          :loading="installing"
          @click="$emit('install', skill)"
        >
          {{ installing ? '安装中…' : '安装到 OpenClaw' }}
        </n-button>
        <n-button
          v-if="installedView && skill.installedId"
          size="small"
          type="warning"
          @click="$emit('uninstall', skill)"
        >
          卸载
        </n-button>
        <n-button
          v-if="skill.homepageUrl"
          size="small"
          quaternary
          tag="a"
          :href="skill.homepageUrl"
          target="_blank"
        >
          打开
        </n-button>
      </n-space>
    </template>
  </n-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NCard, NButton, NTag, NSpace, NRate, NProgress } from 'naive-ui';
import type { MarketSkill } from '@shared/types';

const props = defineProps<{
  skill: MarketSkill;
  installedView?: boolean;
  installing?: boolean;
  installProgressText?: string;
}>();

defineEmits<{
  install: [MarketSkill];
  uninstall: [MarketSkill];
  detail: [MarketSkill];
}>();

const sourceTypeMap = { ClawdHub: 'success', GitHub: 'info' } as const;

const sourceType = computed(() => sourceTypeMap[props.skill.source] || 'default');

const isInstalled = computed(
  () => props.skill.status === 'installed' || props.skill.status === 'update_available',
);

const canInstall = computed(
  () => props.skill.status === 'not_installed' || props.skill.status === 'available',
);

const truncatedDesc = computed(() => {
  const d = props.skill.description || '';
  return d.length > 140 ? `${d.slice(0, 140)}…` : d;
});

function formatCount(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}
</script>

<style scoped>
.skill-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.skill-card--installing {
  border-color: var(--n-color-target);
}
.skill-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.skill-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.skill-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.skill-stats {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}
.stat-text {
  font-size: 11px;
  color: var(--n-text-color-3);
}
.skill-desc {
  flex: 1;
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--n-text-color-2);
  line-height: 1.45;
}
.install-progress {
  margin-bottom: 8px;
}
.install-progress-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3);
}
.skill-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--n-text-color-3);
}
</style>
