<template>
  <div class="page timers-page">
    <HeaderToolbar>
      <n-text v-if="lastRefreshedLabel" depth="3" class="refresh-hint">{{ lastRefreshedLabel }}</n-text>
      <n-button :loading="store.loading" size="small" @click="refresh(true)">
        <template #icon><n-icon><RefreshOutline /></n-icon></template>
        刷新
      </n-button>
      <n-button type="primary" size="small" @click="openCreate">
        <template #icon><n-icon><AddOutline /></n-icon></template>
        新建任务
      </n-button>
      <n-button size="small" quaternary @click="router.push('/chat')">打开对话</n-button>
    </HeaderToolbar>

    <div class="page-body">
      <section class="timers-hero">
        <div class="timers-hero-panel">
          <div class="timers-hero-row">
            <div class="timers-hero-status">
              <GatewayStatusBar
                class="timers-gwb"
                :show-start-stop="false"
                :connection-hint="store.gatewayMeta?.connectionHint ?? 'Gateway 未连接，无法管理定时任务。'"
                :error="store.loadError ?? undefined"
                @clear-error="store.loadError = null"
              />
            </div>
            <div class="timers-hero-stats">
              <div
                v-for="stat in statsCards"
                :key="stat.label"
                class="timers-stat"
                :style="{ '--stat-accent': stat.color }"
              >
                <div class="timers-stat-icon">
                  <n-icon size="16"><component :is="stat.icon" /></n-icon>
                </div>
                <div class="timers-stat-body">
                  <span class="timers-stat-value">{{ stat.value }}</span>
                  <span class="timers-stat-label">{{ stat.label }}</span>
                </div>
              </div>
            </div>
          </div>
          <n-alert
            v-if="store.gatewayMeta?.gatewayConnected && cronDisabled"
            type="warning"
            :show-icon="false"
            class="timers-hero-alert"
          >
            Cron 调度器已禁用。请检查 openclaw.json 的 cron.enabled 或 OPENCLAW_SKIP_CRON。
          </n-alert>
        </div>
      </section>

      <div class="timers-shell">
        <TimerJobList
          :jobs="store.jobs ?? []"
          :selected-id="store.selectedJobId"
          :total="store.total"
          :loading="store.loading"
          :loading-more="store.loadingMore"
          :has-more="store.hasMore"
          :search-query="searchQuery"
          :enabled-filter="enabledFilter"
          :sort-by="sortBy"
          :empty-hint="emptyListHint"
          @update:search-query="onSearch"
          @update:enabled-filter="onEnabledFilter"
          @update:sort-by="onSortBy"
          @select="store.selectJob"
          @run="onRunJob"
          @toggle="onToggleJob"
          @delete="onDeleteJob"
          @load-more="store.loadMore"
          @create="openCreate"
        />
        <TimerJobDetail
          :job="store.selectedJob"
          :runs="currentRuns"
          :runs-loading="store.runsLoading"
          :runs-has-more="currentRunsHasMore"
          :saving="store.saving"
          :running="runningJob"
          :polling-run="pollingRun"
          :last-run-id="lastManualRunId"
          @refresh-runs="refreshRuns"
          @load-more-runs="loadMoreRuns"
          @runs-filter="onRunsFilter"
          @tab-change="onDetailTab"
          @run="onRunSelected"
          @toggle="onToggleSelected"
          @delete="onDeleteSelected"
          @save="onSaveJob"
          @poll-run="onPollRun"
        />
      </div>
    </div>

    <n-drawer v-model:show="createOpen" :width="620" placement="right" class="timers-drawer">
      <n-drawer-content title="新建定时任务" closable class="timers-drawer-content">
        <TimerJobForm
          mode="create"
          show-templates
          :saving="store.saving"
          @cancel="createOpen = false"
          @submit="onCreateJob"
        />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'TimersView' });

import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage, useDialog } from 'naive-ui';
import {
  NAlert, NButton, NDrawer, NDrawerContent, NIcon, NText,
} from 'naive-ui';
import {
  RefreshOutline, AddOutline, TimerOutline, CheckmarkCircleOutline,
  PlayOutline, WarningOutline,
} from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import GatewayStatusBar from '@/components/common/GatewayStatusBar.vue';
import { useCronStore } from '@/stores/cron';
import TimerJobList from './components/TimerJobList.vue';
import TimerJobDetail from './components/TimerJobDetail.vue';
import TimerJobForm from './components/TimerJobForm.vue';
import { ocColors } from '@/utils/chartColors';
import type { CronJob } from '@shared/types';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

const router = useRouter();
const message = useMessage();
const dialog = useDialog();
const store = useCronStore();

const searchQuery = ref('');
const enabledFilter = ref('all');
const sortBy = ref('nextRunAtMs');
const createOpen = ref(false);
const lastRefreshedAt = ref<Date | null>(null);
const runningJob = ref(false);
const pollingRun = ref(false);
const lastManualRunId = ref<string | null>(null);
const runsStatusFilter = ref('all');
const detailTab = ref('overview');

const lastRefreshedLabel = computed(() => {
  if (!lastRefreshedAt.value) return '';
  return lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }) + ' 更新';
});

const cronDisabled = computed(() => {
  const s = store.schedulerStatus;
  if (!s) return false;
  return s.enabled === false;
});

const statsCards = computed(() => {
  const list = store.jobs ?? [];
  const enabled = list.filter((j) => j.enabled !== false).length;
  const running = list.filter((j) => j.status === 'running').length;
  const now = Date.now();
  const errors24h = list.filter((j) => {
    const st = j.state?.lastRunStatus;
    const at = j.state?.lastRunAtMs;
    return st === 'error' && at != null && now - at < ONE_DAY_MS;
  }).length;

  return [
    {
      label: '任务总数',
      value: store.total ?? list.length,
      icon: TimerOutline,
      color: ocColors.primary,
    },
    {
      label: '已启用',
      value: enabled,
      icon: CheckmarkCircleOutline,
      color: ocColors.success,
    },
    {
      label: '运行中',
      value: running,
      icon: PlayOutline,
      color: ocColors.warning,
    },
    {
      label: '近 24h 异常',
      value: errors24h,
      icon: WarningOutline,
      color: ocColors.error,
    },
  ];
});

const emptyListHint = computed(() =>
  store.gatewayMeta?.gatewayConnected
    ? '暂无定时任务，可新建一个。'
    : 'Gateway 未连接，请先启动 Gateway。',
);

const currentRuns = computed(() =>
  store.selectedJobId ? store.getRuns(store.selectedJobId) : [],
);

const currentRunsHasMore = computed(() =>
  store.selectedJobId ? store.runsHasMore(store.selectedJobId) : false,
);

async function refresh(force = false) {
  await store.fetchOverview(force);
  lastRefreshedAt.value = new Date();
}

function onSearch(v: string) {
  searchQuery.value = v;
  store.setQuery(v);
}

function onEnabledFilter(v: string) {
  enabledFilter.value = v;
  store.setListFilters({
    enabled: v as 'all' | 'enabled' | 'disabled',
    includeDisabled: v === 'all' || v === 'disabled',
  });
}

function onSortBy(v: string) {
  sortBy.value = v;
  store.setListFilters({
    sortBy: v as 'nextRunAtMs' | 'updatedAtMs' | 'name',
  });
}

function openCreate() {
  createOpen.value = true;
}

async function onCreateJob(payload: Record<string, unknown>) {
  try {
    await store.createJob(payload);
    createOpen.value = false;
    message.success('定时任务已创建');
    lastRefreshedAt.value = new Date();
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败');
  }
}

async function onSaveJob(patch: Record<string, unknown>) {
  const job = store.selectedJob;
  if (!job) return;
  try {
    await store.patchJob(job.id, patch);
    message.success('已保存');
    detailTab.value = 'overview';
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败');
  }
}

async function onRunJob(job: CronJob) {
  runningJob.value = true;
  try {
    const res = await store.runJobNow(job.id);
    lastManualRunId.value = res?.runId ?? null;
    message.success(res?.runId ? `已入队，runId: ${res.runId}` : '已触发运行');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '运行失败');
  } finally {
    runningJob.value = false;
  }
}

async function onRunSelected() {
  const job = store.selectedJob;
  if (job) await onRunJob(job);
}

async function onToggleJob(job: CronJob) {
  try {
    await store.toggleEnabled(job);
    message.success(job.enabled !== false ? '已禁用' : '已启用');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败');
  }
}

async function onToggleSelected() {
  const job = store.selectedJob;
  if (job) await onToggleJob(job);
}

function onDeleteJob(job: CronJob) {
  dialog.warning({
    title: '删除定时任务',
    content: `确定删除「${job.name || job.id}」？此操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteJob(job.id);
        message.success('已删除');
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败');
      }
    },
  });
}

async function onDeleteSelected() {
  const job = store.selectedJob;
  if (job) await onDeleteJob(job);
}

async function onDetailTab(tab: string) {
  detailTab.value = tab;
  if (tab === 'history' && store.selectedJobId) {
    await store.fetchRuns(store.selectedJobId);
  }
}

async function refreshRuns() {
  if (store.selectedJobId) {
    await store.fetchRuns(store.selectedJobId, true);
  }
}

async function loadMoreRuns() {
  if (store.selectedJobId) {
    await store.fetchRuns(store.selectedJobId, false, true);
  }
}

function onRunsFilter(status: string) {
  runsStatusFilter.value = status;
  if (store.selectedJobId) {
    store.fetchRuns(store.selectedJobId, true);
  }
}

async function onPollRun(runId: string) {
  const jobId = store.selectedJobId;
  if (!jobId) return;
  pollingRun.value = true;
  try {
    const entry = await store.pollRunResult(jobId, runId);
    if (entry?.status === 'ok') message.success('运行成功');
    else if (entry?.status === 'error') message.error(entry.error ?? '运行失败');
    else if (entry?.status === 'skipped') message.warning(entry.reason ?? '运行已跳过');
    else message.info('轮询结束，请查看执行历史');
  } finally {
    pollingRun.value = false;
  }
}

onMounted(() => {
  store.listParams.includeDisabled = true;
  refresh(true);
});

onUnmounted(() => {
  store.dispose();
});

watch(
  () => store.selectedJobId,
  () => {
    detailTab.value = 'overview';
    lastManualRunId.value = null;
  },
);
</script>

<style scoped>
.timers-page {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--oc-timer-text);
}

.timers-page .page-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 16px;
}

.refresh-hint {
  font-size: 12px;
  color: var(--oc-timer-text-muted);
}

.timers-hero {
  flex-shrink: 0;
}

.timers-hero-panel {
  border-radius: var(--oc-radius-md);
  background: var(--oc-stat-bg);
  border: 1px solid var(--oc-timer-panel-border);
  box-shadow: var(--oc-shadow-card);
  overflow: hidden;
}

.timers-hero-row {
  display: flex;
  align-items: center;
  gap: 0;
  min-height: 52px;
  padding: 6px 10px 6px 12px;
}

.timers-hero-status {
  flex: 0 0 auto;
  padding-right: 14px;
  margin-right: 6px;
  border-right: 1px solid var(--oc-timer-panel-border);
}

.timers-hero-status :deep(.gateway-status-bar) {
  margin-bottom: 0;
}

.timers-hero-status :deep(.gwb-card) {
  --n-padding-top: 0;
  --n-padding-bottom: 0;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

.timers-hero-status :deep(.gwb-inner) {
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
}

.timers-hero-status :deep(.gwb-leading) {
  flex-shrink: 0;
}

.timers-hero-status :deep(.gwb-title-row) {
  gap: 6px;
  flex-wrap: nowrap;
}

.timers-hero-status :deep(.gwb-tag) {
  font-size: 10px;
  padding: 0 7px;
  height: 20px;
}

.timers-hero-status :deep(.gwb-alert) {
  margin-top: 8px;
}

.timers-hero-row:has(:deep(.gwb-alert)) {
  flex-wrap: wrap;
  align-items: flex-start;
  padding-bottom: 10px;
}

.timers-hero-row:has(:deep(.gwb-alert)) .timers-hero-stats {
  flex: 1 1 100%;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--oc-timer-panel-border);
}

.timers-hero-alert {
  margin: 0;
  border-radius: 0;
  border-top: 1px solid var(--oc-timer-panel-border);
}

.timers-hero-stats {
  flex: 1 1 0;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
}

.timers-stat {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  min-width: 0;
  border-radius: 6px;
  background: transparent;
  border: none;
  border-left: 1px solid var(--oc-timer-panel-border);
  transition: background var(--oc-transition);
}

.timers-stat:first-child {
  border-left: none;
}

.timers-stat:hover {
  background: color-mix(in srgb, var(--stat-accent, var(--oc-primary)) 8%, transparent);
}

.timers-stat-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--stat-accent, var(--oc-primary));
  background: color-mix(in srgb, var(--stat-accent, var(--oc-primary)) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--stat-accent, var(--oc-primary)) 18%, transparent);
}

.timers-stat-body {
  min-width: 0;
  flex: 1;
}

.timers-stat-value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--oc-timer-text);
}

.timers-stat-label {
  display: block;
  font-size: 10px;
  font-weight: 600;
  color: var(--oc-timer-text-muted);
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.timers-shell {
  display: grid;
  grid-template-columns: minmax(0, 340px) minmax(0, 1fr);
  gap: 16px;
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

@media (max-width: 1200px) {
  .timers-hero-row {
    flex-wrap: wrap;
    padding: 10px 12px;
    gap: 10px;
  }

  .timers-hero-status {
    flex: 1 1 100%;
    padding-right: 0;
    margin-right: 0;
    border-right: none;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--oc-timer-panel-border);
  }

  .timers-hero-stats {
    flex: 1 1 100%;
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .timers-stat {
    border-left: none;
    background: color-mix(in srgb, var(--oc-stat-bg) 88%, var(--oc-timer-panel-border));
    border: 1px solid var(--oc-stat-border);
    border-radius: calc(var(--oc-radius-md) - 2px);
  }
}

@media (max-width: 720px) {
  .timers-hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
}

@media (max-width: 960px) {
  .timers-page {
    overflow: visible;
  }

  .timers-shell {
    grid-template-columns: 1fr;
    flex: none;
    overflow: visible;
  }
}
</style>
