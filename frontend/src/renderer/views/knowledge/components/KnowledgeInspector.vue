<template>
  <aside class="kb-inspector-panel">
    <div class="insp-head">
      <span class="insp-kind">{{ kindLabel }}</span>
      <n-button quaternary circle size="tiny" @click="$emit('close')">
        <template #icon><n-icon><close-outline /></n-icon></template>
      </n-button>
    </div>
    <n-text strong class="insp-title">{{ title }}</n-text>
    <n-tag v-if="path" size="small" round :bordered="false">{{ path }}</n-tag>
    <n-alert
      v-if="isAgents"
      type="info"
      size="small"
      :bordered="false"
      class="insp-agents-hint"
    >
      编辑后可在角色配置页同步到 openclaw.json 的 systemPrompt。
      <n-button size="tiny" quaternary :loading="syncing" @click="syncAgents">
        同步 systemPrompt
      </n-button>
    </n-alert>
    <n-scrollbar class="insp-body">
      <pre v-if="content" class="insp-pre">{{ content }}</pre>
      <p v-else-if="snippet" class="insp-snippet">{{ snippet }}</p>
      <n-empty v-else size="small" description="无预览内容" />
    </n-scrollbar>
    <div v-if="path" class="insp-foot">
      <n-button size="small" type="primary" @click="$emit('edit')">编辑</n-button>
      <n-button size="small" quaternary @click="copyRef">复制路径</n-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  useMessage,
  NButton,
  NIcon,
  NScrollbar,
  NEmpty,
  NTag,
  NText,
  NAlert,
} from 'naive-ui';
import { CloseOutline } from '@vicons/ionicons5';
import { configApi } from '@/api/config';
import type { KnowledgeGraphNode } from '@shared/types';

const props = defineProps<{
  node: KnowledgeGraphNode | null;
  path?: string | null;
  content?: string;
  snippet?: string;
}>();

defineEmits<{ close: []; edit: [] }>();

const message = useMessage();
const syncing = ref(false);
const title = computed(() => props.node?.label ?? props.path ?? '记忆');
const path = computed(() => props.node?.path ?? props.path);
const isAgents = computed(() =>
  props.node?.kind === 'agents'
  || path.value?.toUpperCase() === 'AGENTS.MD',
);

const kindLabel = computed(() => {
  const k = props.node?.kind;
  const map: Record<string, string> = {
    hub: '长期',
    daily: '日记',
    dream: '梦境',
    dream_shard: '梦境片段',
    soul: '灵魂',
    user: '用户',
    agents: '工作区',
    chunk: '条目',
    topic: '主题',
  };
  return map[k ?? ''] ?? '文件';
});

function copyRef() {
  const p = path.value;
  if (!p) return;
  const line = props.node?.lineStart;
  navigator.clipboard.writeText(line ? `${p}:${line}` : p)
    .then(() => message.success('已复制'))
    .catch(() => message.warning('复制失败'));
}

async function syncAgents() {
  syncing.value = true;
  try {
    const res = await configApi.syncBootstrapPrompt();
    message.success(res.data.message ?? '已同步 AGENTS.md');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '同步失败');
  } finally {
    syncing.value = false;
  }
}
</script>

<style scoped>
.kb-inspector-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  color: var(--oc-knowledge-detail-text);
}

.insp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px 4px;
}

.insp-kind {
  font-size: 11px;
  font-weight: 600;
  color: var(--oc-primary);
  text-transform: uppercase;
}

.insp-title {
  padding: 0 12px 6px;
  font-size: 14px !important;
  color: var(--oc-knowledge-detail-title) !important;
}

.kb-inspector-panel :deep(.n-tag) {
  margin: 0 12px 8px;
}

.insp-agents-hint {
  margin: 0 12px 8px;
}

.insp-body {
  flex: 1;
  min-height: 0;
  padding: 0 12px;
}

.insp-pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, Menlo, monospace;
  color: var(--oc-knowledge-detail-text);
}

.insp-snippet {
  margin: 0;
  font-size: 12px;
  color: var(--oc-knowledge-detail-muted);
}

.insp-foot {
  flex-shrink: 0;
  padding: 10px 12px;
  display: flex;
  gap: 8px;
  border-top: 1px solid var(--oc-knowledge-detail-border);
}
</style>
