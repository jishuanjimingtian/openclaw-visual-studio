<template>
  <button
    type="button"
    class="job-card"
    :class="cardClass"
    @click="emit('select', job.id)"
  >
    <div class="job-card-top">
      <div class="job-avatar" :class="avatarClass">
        <n-icon size="16"><TimerOutline /></n-icon>
      </div>
      <div class="job-card-main">
        <div class="job-card-title-row">
          <span class="job-name">{{ job.name || job.id }}</span>
          <n-tag size="small" :type="statusTagType(job.status)" round :bordered="false">
            {{ statusLabel }}
          </n-tag>
        </div>
        <p class="job-schedule">{{ scheduleSummary(job) }}</p>
        <div class="job-card-meta">
          <n-tag size="tiny" round :bordered="false" class="session-tag">
            {{ job.sessionTarget ?? '—' }}
          </n-tag>
          <span v-if="job.agentId" class="meta-chip">@{{ job.agentId }}</span>
          <span class="meta-time">
            <n-icon size="12"><TimeOutline /></n-icon>
            {{ nextRunLabel }}
          </span>
        </div>
      </div>
    </div>
    <div class="job-card-actions" @click.stop>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="emit('run', job)">
            <template #icon><n-icon><PlayOutline /></n-icon></template>
          </n-button>
        </template>
        立即运行
      </n-tooltip>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="emit('toggle', job)">
            <template #icon><n-icon><component :is="toggleIcon" /></n-icon></template>
          </n-button>
        </template>
        {{ job.enabled !== false ? '禁用' : '启用' }}
      </n-tooltip>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="emit('delete', job)">
            <template #icon><n-icon><TrashOutline /></n-icon></template>
          </n-button>
        </template>
        删除
      </n-tooltip>
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NButton, NIcon, NTag, NTooltip } from 'naive-ui';
import {
  PlayOutline, PauseOutline, PlayCircleOutline, TrashOutline,
  TimerOutline, TimeOutline,
} from '@vicons/ionicons5';
import { scheduleSummary, statusTagType } from '../cronFormUtils';
import type { CronJob } from '@shared/types';

const props = defineProps<{
  job: CronJob;
  active?: boolean;
}>();

const emit = defineEmits<{
  select: [id: string];
  run: [job: CronJob];
  toggle: [job: CronJob];
  delete: [job: CronJob];
}>();

const statusLabel = computed(() => {
  const s = props.job.status ?? 'idle';
  const map: Record<string, string> = {
    disabled: '已禁用',
    running: '运行中',
    ok: '正常',
    error: '异常',
    skipped: '跳过',
    idle: '空闲',
  };
  return map[s] ?? s;
});

const cardClass = computed(() => ({
  active: props.active,
  running: props.job.status === 'running',
  error: props.job.status === 'error' || props.job.state?.lastRunStatus === 'error',
  disabled: props.job.enabled === false,
}));

const avatarClass = computed(() => ({
  running: props.job.status === 'running',
}));

const toggleIcon = computed(() =>
  props.job.enabled !== false ? PauseOutline : PlayCircleOutline,
);

const nextRunLabel = computed(() => {
  const ms = props.job.nextRunAtMs;
  if (!ms) return '无计划';
  return new Date(ms).toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
});
</script>

<style scoped>
.job-card {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: var(--oc-radius-md);
  padding: 12px;
  margin-bottom: 8px;
  background: transparent;
  cursor: pointer;
  color: inherit;
  font: inherit;
  transition:
    background var(--oc-transition),
    border-color var(--oc-transition),
    box-shadow var(--oc-transition);
}

.job-card:hover {
  background: var(--oc-timer-card-hover);
}

.job-card.active {
  background: var(--oc-timer-card-active);
  border-color: var(--oc-primary);
  box-shadow: inset 3px 0 0 var(--oc-primary);
}

.job-card.running:not(.active) {
  border-color: var(--oc-timer-running-ring);
}

.job-card.error:not(.active) {
  border-color: var(--oc-timer-error-ring);
}

.job-card.disabled {
  opacity: 0.72;
}

.job-card-top {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.job-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oc-primary);
  background: color-mix(in srgb, var(--oc-primary) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--oc-primary) 24%, transparent);
}

.job-avatar.running {
  color: var(--oc-warning);
  background: color-mix(in srgb, var(--oc-warning) 16%, transparent);
  border-color: color-mix(in srgb, var(--oc-warning) 35%, transparent);
}

.job-card-main {
  min-width: 0;
  flex: 1;
}

.job-card-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.job-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--oc-timer-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.job-schedule {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--oc-timer-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.session-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta-chip {
  font-size: 11px;
  color: var(--oc-timer-text-muted);
}

.meta-time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--oc-timer-text-muted);
  margin-left: auto;
}

.job-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed color-mix(in srgb, var(--oc-timer-panel-border) 80%, transparent);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--oc-transition);
}

.job-card:hover .job-card-actions,
.job-card.active .job-card-actions {
  opacity: 1;
  pointer-events: auto;
}
</style>
