<template>
  <div class="models-overview">
    <header class="models-status">
      <div class="models-status-leading">
        <span class="models-status-dot" :class="configExists ? 'is-on' : 'is-off'" />
        <div>
          <span class="models-status-title">OpenClaw 模型配置</span>
          <span class="models-status-sub">与本地 openclaw.json 同步</span>
        </div>
        <n-tag v-if="configExists" size="small" type="success" round :bordered="false">已连接</n-tag>
        <n-tag v-else size="small" type="warning" round :bordered="false">未找到</n-tag>
      </div>
      <div class="models-status-actions">
        <n-button size="small" :loading="syncing" type="primary" @click="$emit('sync')">
          导入到本平台
        </n-button>
      </div>
    </header>

    <section class="models-metrics">
      <div class="models-metric">
        <div class="models-metric-icon">
          <n-icon :size="17"><star-outline /></n-icon>
        </div>
        <div class="models-metric-body">
          <span class="models-metric-label">默认主模型</span>
          <span class="models-metric-value mono">{{ overview.primaryModelRef || '未设置' }}</span>
        </div>
      </div>
      <div class="models-metric">
        <div class="models-metric-icon models-metric-icon--fallback">
          <n-icon :size="17"><layers-outline /></n-icon>
        </div>
        <div class="models-metric-body">
          <span class="models-metric-label">备用模型</span>
          <span class="models-metric-value">{{ fallbackCount }} 个</span>
        </div>
      </div>
      <div class="models-metric">
        <div class="models-metric-icon models-metric-icon--count">
          <n-icon :size="17"><hardware-chip-outline /></n-icon>
        </div>
        <div class="models-metric-body">
          <span class="models-metric-label">已注册</span>
          <span class="models-metric-value">{{ overview.models.length }} 个</span>
        </div>
      </div>
      <div class="models-metric">
        <div class="models-metric-icon">
          <n-icon :size="17"><document-outline /></n-icon>
        </div>
        <div class="models-metric-body">
          <span class="models-metric-label">配置文件</span>
          <n-tooltip v-if="overview.configPath" trigger="hover">
            <template #trigger>
              <span class="models-metric-value mono">{{ shortPath }}</span>
            </template>
            {{ overview.configPath }}
          </n-tooltip>
          <span v-else class="models-metric-value">—</span>
        </div>
      </div>
    </section>

    <n-collapse
      v-if="hasExtraContent"
      v-model:expanded-names="detailExpandedNames"
      class="models-overview-collapse"
      @update:expanded-names="onDetailExpandChange"
    >
      <n-collapse-item name="detail">
        <template #header>
          <div class="models-overview-collapse-head">
            <span class="models-overview-collapse-title">配置详情</span>
            <span class="models-overview-collapse-meta">{{ detailSummary }}</span>
          </div>
        </template>
        <div class="models-overview-extra">
          <div v-if="overview.fallbackModelRefs?.length" class="models-overview-block">
            <span class="models-overview-extra-title">备用链（按优先级）</span>
            <div class="models-chip-row">
              <span
                v-for="(fb, i) in overview.fallbackModelRefs"
                :key="fb"
                class="models-chip"
              >
                {{ i + 1 }}. {{ fb }}
              </span>
            </div>
          </div>

          <div v-if="overview.models.length > 0" class="models-overview-block">
            <span class="models-overview-extra-title">已注册模型</span>
            <div class="models-chip-row">
              <span
                v-for="entry in overview.models"
                :key="entry.modelRef"
                :class="['models-chip', { 'models-chip--primary': entry.primary }]"
              >
                {{ entry.modelRef }}
                <template v-if="entry.primary"> · 默认</template>
                <template v-else-if="entry.apiKeyConfigured"> · Key</template>
              </span>
            </div>
          </div>

          <div v-if="hasEndpoints" class="models-endpoints">
            <div v-if="overview.qwenBaseUrl" class="models-endpoint-line">
              Qwen：<code>{{ overview.qwenBaseUrl }}</code>
            </div>
            <div v-if="overview.deepseekBaseUrl" class="models-endpoint-line">
              DeepSeek：<code>{{ overview.deepseekBaseUrl }}</code>
            </div>
          </div>

          <p v-if="overview.configPath" class="models-path">{{ overview.configPath }}</p>
        </div>
      </n-collapse-item>
    </n-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { NButton, NIcon, NTag, NTooltip, NCollapse, NCollapseItem } from 'naive-ui';
import {
  StarOutline, LayersOutline, HardwareChipOutline, DocumentOutline,
} from '@vicons/ionicons5';
import type { OpenClawModelOverview } from '@shared/types';

const props = defineProps<{
  overview: OpenClawModelOverview;
  configExists: boolean;
  syncing?: boolean;
}>();

defineEmits<{
  sync: [];
}>();

const DETAIL_EXPAND_KEY = 'ocvs-models-overview-detail-open';

const detailExpandedNames = ref<string[]>([]);

function readDetailExpanded(): boolean {
  try {
    const raw = localStorage.getItem(DETAIL_EXPAND_KEY);
    if (raw === null) return false;
    return raw === 'true';
  } catch {
    return false;
  }
}

function persistDetailExpanded(open: boolean) {
  try {
    localStorage.setItem(DETAIL_EXPAND_KEY, String(open));
  } catch {
    // ignore
  }
}

onMounted(() => {
  detailExpandedNames.value = readDetailExpanded() ? ['detail'] : [];
});

function onDetailExpandChange(names: string[] | null) {
  const open = (names ?? []).includes('detail');
  persistDetailExpanded(open);
}

const fallbackCount = computed(() => props.overview.fallbackModelRefs?.length ?? 0);

const hasEndpoints = computed(
  () => Boolean(props.overview.qwenBaseUrl || props.overview.deepseekBaseUrl),
);

const hasExtraContent = computed(
  () =>
    props.overview.models.length > 0
    || fallbackCount.value > 0
    || hasEndpoints.value
    || Boolean(props.overview.configPath),
);

const detailSummary = computed(() => {
  const parts: string[] = [];
  if (fallbackCount.value > 0) parts.push(`备用 ${fallbackCount.value}`);
  if (props.overview.models.length > 0) parts.push(`注册 ${props.overview.models.length}`);
  if (hasEndpoints.value) parts.push('端点');
  return parts.length ? parts.join(' · ') : '展开查看';
});

const shortPath = computed(() => {
  const p = props.overview.configPath;
  if (!p) return '—';
  let s = p.replace(/\\/g, '/');
  s = s.replace(/^[A-Za-z]:\/Users\/[^/]+/, '~');
  s = s.replace(/^\/Users\/[^/]+/, '~');
  const parts = s.split('/').filter(Boolean);
  if (parts.length <= 3) return s;
  return `~/${parts.slice(-2).join('/')}`;
});
</script>

<style scoped lang="scss">
@use '../models-overview.scss';

.models-overview-block + .models-overview-block {
  margin-top: 12px;
}
</style>
