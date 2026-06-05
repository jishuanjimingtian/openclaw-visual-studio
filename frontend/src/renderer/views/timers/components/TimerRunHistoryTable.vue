<template>
  <div class="run-history">
    <n-space v-if="showFilter" :size="8" style="margin-bottom: 12px">
      <n-select
        v-model:value="statusFilter"
        size="small"
        :options="statusOptions"
        style="width: 140px"
        @update:value="onFilterChange"
      />
      <n-button size="small" :loading="loading" @click="emit('refresh')">刷新历史</n-button>
    </n-space>

    <div v-if="loading && entries.length === 0" class="run-loading">
      <n-spin size="medium" />
    </div>

    <n-empty v-else-if="entries.length === 0" description="暂无执行记录" size="small" />

    <n-data-table
      v-else
      size="small"
      :columns="columns"
      :data="entries"
      :bordered="false"
      :row-key="rowKey"
    />

    <n-button
      v-if="hasMore"
      block
      quaternary
      size="small"
      :loading="loading"
      style="margin-top: 8px"
      @click="emit('load-more')"
    >
      加载更多
    </n-button>

    <n-drawer v-model:show="detailOpen" :width="480" placement="right">
      <n-drawer-content title="运行详情" closable>
        <pre class="run-json">{{ detailJson }}</pre>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h } from 'vue';
import {
  NButton, NDataTable, NDrawer, NDrawerContent, NEmpty, NSelect, NSpace, NSpin, NTag, NText,
} from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import type { CronRunEntry } from '@shared/types';

const props = defineProps<{
  entries: CronRunEntry[];
  loading?: boolean;
  hasMore?: boolean;
  showFilter?: boolean;
}>();

const emit = defineEmits<{
  refresh: [];
  'load-more': [];
  'filter-change': [status: string];
}>();

const statusFilter = ref('all');
const detailOpen = ref(false);
const detailJson = ref('');

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '成功', value: 'ok' },
  { label: '失败', value: 'error' },
  { label: '跳过', value: 'skipped' },
];

function formatTime(ms?: number) {
  if (!ms) return '—';
  return new Date(ms).toLocaleString('zh-CN');
}

function rowKey(row: CronRunEntry) {
  return row.runId ?? `${row.startedAtMs}-${row.status}`;
}

function openDetail(row: CronRunEntry) {
  detailJson.value = JSON.stringify(row, null, 2);
  detailOpen.value = true;
}

function onFilterChange(v: string) {
  emit('filter-change', v);
}

const columns = computed<DataTableColumns<CronRunEntry>>(() => [
  {
    title: '时间',
    key: 'startedAtMs',
    width: 160,
    render: (row) => formatTime(row.startedAtMs ?? row.finishedAtMs),
  },
  {
    title: '状态',
    key: 'status',
    width: 88,
    render: (row) => {
      const t = row.status === 'ok' ? 'success' : row.status === 'error' ? 'error' : row.status === 'skipped' ? 'warning' : 'default';
      return h(NTag, { size: 'small', type: t, bordered: false }, () => row.status ?? '—');
    },
  },
  {
    title: '耗时',
    key: 'durationMs',
    width: 72,
    render: (row) => {
      const d = row.durationMs ?? (row.finishedAtMs && row.startedAtMs ? row.finishedAtMs - row.startedAtMs : null);
      return d != null ? `${Math.round(d / 1000)}s` : '—';
    },
  },
  {
    title: '摘要',
    key: 'error',
    ellipsis: { tooltip: true },
    render: (row) => row.error ?? row.reason ?? row.runId ?? '—',
  },
  {
    title: '',
    key: 'actions',
    width: 64,
    render: (row) => h(NButton, { size: 'tiny', quaternary: true, onClick: () => openDetail(row) }, () => '详情'),
  },
]);
</script>

<style scoped>
.run-history :deep(.n-data-table) {
  --n-th-color: var(--oc-surface-elevated);
  --n-td-color: transparent;
}

.run-history :deep(.n-data-table-th) {
  color: var(--oc-timer-text-muted) !important;
  font-size: 12px;
}

.run-history :deep(.n-data-table-td) {
  color: var(--oc-timer-text) !important;
  font-size: 13px;
}

.run-loading {
  display: flex;
  justify-content: center;
  padding: 32px;
}

.run-json {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  padding: 12px;
  border-radius: var(--oc-radius-md);
  background: var(--oc-timer-code-bg);
  border: 1px solid var(--oc-timer-code-border);
  color: var(--oc-timer-text);
}
</style>
