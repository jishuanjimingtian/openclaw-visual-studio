<template>
  <div class="file-list">
    <n-empty v-if="!files.length" size="small" description="暂无记忆文件" />
    <template v-else>
      <section v-for="section in sections" :key="section.id" class="file-section">
        <div class="section-head">
          <div class="section-title">{{ section.title }}</div>
          <n-input
            v-if="section.id === 'daily'"
            v-model:value="dailyFilter"
            size="tiny"
            placeholder="筛选日记…"
            clearable
            class="daily-filter"
          />
        </div>
        <template v-if="section.id === 'daily' && virtualDaily.length > 80">
          <n-virtual-list
            :items="virtualDaily"
            :item-size="32"
            key-field="path"
            class="daily-virtual"
          >
            <template #default="{ item }">
              <button
                type="button"
                class="file-item"
                :class="{ active: selectedPath === item.path }"
                @click="emit('select', item.path)"
              >
                <span class="file-label">{{ labelFor(item) }}</span>
                <span v-if="item.sizeBytes" class="file-meta">{{ formatSize(item.sizeBytes) }}</span>
              </button>
            </template>
          </n-virtual-list>
        </template>
        <template v-else>
          <button
            v-for="f in section.items"
            :key="f.path"
            type="button"
            class="file-item"
            :class="{ active: selectedPath === f.path }"
            @click="emit('select', f.path)"
          >
            <span class="file-label">{{ labelFor(f) }}</span>
            <span v-if="f.sizeBytes" class="file-meta">{{ formatSize(f.sizeBytes) }}</span>
          </button>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { NEmpty, NInput, NVirtualList } from 'naive-ui';
import type { KnowledgeFile } from '@shared/types';

const props = defineProps<{
  files: KnowledgeFile[];
  selectedPath: string | null;
}>();

const emit = defineEmits<{ select: [path: string] }>();

const dailyFilter = ref('');

const configFiles = computed(() =>
  props.files.filter((f) => ['agents', 'soul', 'user'].includes(String(f.kind))),
);
const hubFiles = computed(() => props.files.filter((f) => f.kind === 'hub'));
const dailyFiles = computed(() => {
  const q = dailyFilter.value.trim().toLowerCase();
  return props.files
    .filter((f) => f.kind === 'daily')
    .filter((f) => !q || f.path.toLowerCase().includes(q))
    .sort((a, b) => b.path.localeCompare(a.path));
});
const dreamFiles = computed(() =>
  props.files.filter((f) => f.kind === 'dream' || f.kind === 'dream_shard'),
);
const otherFiles = computed(() =>
  props.files.filter(
    (f) => !['hub', 'daily', 'dream', 'dream_shard', 'agents', 'soul', 'user'].includes(String(f.kind)),
  ),
);

const virtualDaily = computed(() => dailyFiles.value);

const sections = computed(() => {
  const out: { id: string; title: string; items: KnowledgeFile[] }[] = [];
  if (configFiles.value.length) {
    out.push({ id: 'config', title: '工作区设定', items: configFiles.value });
  }
  if (hubFiles.value.length) {
    out.push({ id: 'hub', title: '长期记忆', items: hubFiles.value });
  }
  if (dailyFiles.value.length || props.files.some((f) => f.kind === 'daily')) {
    out.push({ id: 'daily', title: '每日笔记', items: dailyFiles.value });
  }
  if (dreamFiles.value.length) {
    out.push({ id: 'dream', title: '梦境', items: dreamFiles.value });
  }
  if (otherFiles.value.length) {
    out.push({ id: 'other', title: '其他', items: otherFiles.value });
  }
  if (!out.length && props.files.length) {
    out.push({ id: 'all', title: '全部文件', items: [...props.files] });
  }
  return out;
});

function labelFor(f: KnowledgeFile) {
  if (f.kind === 'hub') return 'MEMORY.md';
  if (f.kind === 'dream') return 'DREAMS.md';
  if (f.kind === 'dream_shard') return f.path.replace(/^memory\/.dreams\//, '');
  if (f.kind === 'soul') return 'SOUL.md';
  if (f.kind === 'user') return 'USER.md';
  if (f.kind === 'agents') return 'AGENTS.md';
  return f.path.replace(/^memory\//, '');
}

function formatSize(n: number) {
  if (n < 1024) return `${n}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)}K`;
  return `${(n / (1024 * 1024)).toFixed(1)}M`;
}
</script>

<style scoped>
.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 4px 4px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--oc-knowledge-detail-muted);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.daily-filter {
  max-width: 100%;
}

.daily-virtual {
  max-height: 280px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: var(--oc-knowledge-detail-title);
  transition: background 0.15s;
}

.file-item:hover {
  background: var(--oc-chat-session-active);
}

.file-item.active {
  background: color-mix(in srgb, var(--oc-primary) 14%, transparent);
  color: var(--oc-primary);
  font-weight: 600;
}

.file-label {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--oc-knowledge-detail-muted);
}
</style>
