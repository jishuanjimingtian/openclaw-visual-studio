<template>
  <aside class="timer-panel">
    <header class="panel-header">
      <div class="panel-title-row">
        <n-icon size="18" class="panel-title-icon"><TimerOutline /></n-icon>
        <span class="panel-title">任务列表</span>
        <n-tag size="small" round :bordered="false" class="count-tag">{{ displayCount }}</n-tag>
      </div>
      <n-button size="tiny" quaternary type="primary" @click="emit('create')">
        <template #icon><n-icon><AddOutline /></n-icon></template>
        新建
      </n-button>
    </header>

    <div class="panel-toolbar">
      <n-input
        :value="searchQuery"
        placeholder="搜索名称、调度、提示词..."
        clearable
        size="small"
        class="search-input"
        @update:value="emit('update:searchQuery', $event)"
      >
        <template #prefix>
          <n-icon><SearchOutline /></n-icon>
        </template>
      </n-input>
      <div class="filter-row">
        <n-select
          :value="enabledFilter"
          size="small"
          :options="enabledOptions"
          placeholder="状态"
          @update:value="emit('update:enabledFilter', $event)"
        />
        <n-select
          :value="sortBy"
          size="small"
          :options="sortOptions"
          placeholder="排序"
          @update:value="emit('update:sortBy', $event)"
        />
      </div>
    </div>

    <div class="panel-body">
      <div v-if="loading && jobs.length === 0" class="panel-loading">
        <n-skeleton v-for="i in 5" :key="i" text :repeat="2" />
      </div>

      <EmptyState v-else-if="!loading && jobs.length === 0" :description="emptyHint">
        <template #actions>
          <n-button size="small" type="primary" @click="emit('create')">新建任务</n-button>
        </template>
      </EmptyState>

      <div v-else class="job-scroll">
        <TimerJobRow
          v-for="job in jobs"
          :key="job.id"
          :job="job"
          :active="job.id === selectedId"
          @select="emit('select', $event)"
          @run="emit('run', $event)"
          @toggle="emit('toggle', $event)"
          @delete="emit('delete', $event)"
        />
      </div>

      <footer v-if="hasMore || jobs.length > 0" class="panel-footer">
        <n-button
          v-if="hasMore"
          block
          quaternary
          size="small"
          :loading="loadingMore"
          @click="emit('load-more')"
        >
          加载更多
        </n-button>
        <n-text v-else depth="3" class="footer-hint">已显示全部 {{ jobs.length }} 项</n-text>
      </footer>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NButton, NIcon, NInput, NSelect, NSkeleton, NTag, NText } from 'naive-ui';
import { SearchOutline, TimerOutline, AddOutline } from '@vicons/ionicons5';
import EmptyState from '@/components/EmptyState.vue';
import TimerJobRow from './TimerJobRow.vue';
import type { CronJob } from '@shared/types';

const props = defineProps<{
  jobs: CronJob[];
  selectedId: string | null;
  total?: number;
  loading?: boolean;
  loadingMore?: boolean;
  hasMore?: boolean;
  searchQuery: string;
  enabledFilter: string;
  sortBy: string;
  emptyHint?: string;
}>();

const emit = defineEmits<{
  'update:searchQuery': [v: string];
  'update:enabledFilter': [v: string];
  'update:sortBy': [v: string];
  select: [id: string];
  run: [job: CronJob];
  toggle: [job: CronJob];
  delete: [job: CronJob];
  'load-more': [];
  create: [];
}>();

const displayCount = computed(() => {
  const t = props.total ?? props.jobs.length;
  return props.hasMore ? `${props.jobs.length}/${t}` : String(t);
});

const enabledOptions = [
  { label: '全部', value: 'all' },
  { label: '已启用', value: 'enabled' },
  { label: '已禁用', value: 'disabled' },
];

const sortOptions = [
  { label: '下次运行', value: 'nextRunAtMs' },
  { label: '更新时间', value: 'updatedAtMs' },
  { label: '名称', value: 'name' },
];
</script>

<style scoped>
.timer-panel {
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--oc-timer-panel-bg);
  border: 1px solid var(--oc-timer-panel-border);
  border-radius: var(--oc-radius-lg);
  border-left: 3px solid var(--oc-primary);
  color: var(--oc-timer-text);
  box-shadow: var(--oc-shadow-card);
}

.panel-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--oc-timer-panel-border);
  background: var(--oc-surface-elevated);
}

.panel-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-title-icon {
  color: var(--oc-primary);
  flex-shrink: 0;
}

.panel-title {
  font-weight: 700;
  font-size: 14px;
}

.count-tag {
  font-variant-numeric: tabular-nums;
}

.panel-toolbar {
  flex-shrink: 0;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--oc-timer-panel-border);
  background: color-mix(in srgb, var(--oc-surface-elevated) 60%, transparent);
}

.filter-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-loading {
  padding: 14px;
}

.job-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 10px;
}

.panel-footer {
  flex-shrink: 0;
  padding: 10px 14px;
  border-top: 1px solid var(--oc-timer-panel-border);
  background: var(--oc-surface-elevated);
}

.footer-hint {
  display: block;
  text-align: center;
  font-size: 11px;
}

.timer-panel :deep(.n-input),
.timer-panel :deep(.n-base-selection) {
  --n-border: 1px solid var(--oc-timer-panel-border);
}

.timer-panel :deep(.n-empty .n-empty__description) {
  color: var(--oc-timer-text-muted);
}
</style>
