<template>
  <main class="timer-detail">
    <template v-if="job">
      <header class="detail-header">
        <div class="detail-header-main">
          <h2 class="detail-title">{{ job.name || '未命名任务' }}</h2>
          <p class="detail-subtitle">{{ scheduleSummary(job) }}</p>
          <div class="detail-tags">
            <n-tag :type="statusTagType(job.status)" size="small" round :bordered="false">
              {{ statusLabel }}
            </n-tag>
            <n-tag size="small" round :bordered="false">{{ job.sessionTarget ?? '—' }}</n-tag>
            <n-tag v-if="job.enabled === false" size="small" type="default" round>已禁用</n-tag>
          </div>
        </div>
        <div class="detail-header-actions">
          <n-button size="small" :loading="running" type="primary" @click="emit('run')">
            <template #icon><n-icon><PlayOutline /></n-icon></template>
            运行
          </n-button>
          <n-button size="small" @click="emit('toggle')">
            {{ job.enabled !== false ? '禁用' : '启用' }}
          </n-button>
        </div>
      </header>

      <div class="detail-tabs-wrap">
        <n-tabs
          v-model:value="activeTab"
          type="line"
          :animated="false"
          class="detail-tabs"
          @update:value="onTabChange"
        >
          <n-tab-pane name="overview" tab="概览">
            <div class="tab-pane-inner">
              <n-descriptions
                :column="2"
                label-placement="left"
                bordered
                size="small"
                class="detail-desc"
              >
                <n-descriptions-item label="任务 ID" :span="2">
                  <n-text code class="id-code">{{ job.id }}</n-text>
                  <n-button text size="tiny" class="copy-btn" @click="copyId">复制</n-button>
                </n-descriptions-item>
                <n-descriptions-item label="下次运行">{{ formatTime(job.nextRunAtMs) }}</n-descriptions-item>
                <n-descriptions-item label="Agent">{{ job.agentId || '默认 (main)' }}</n-descriptions-item>
                <n-descriptions-item label="最近运行">
                  {{ job.state?.lastRunStatus ?? '—' }}
                </n-descriptions-item>
                <n-descriptions-item label="最近时间">
                  {{ job.state?.lastRunAtMs ? formatTime(job.state.lastRunAtMs) : '—' }}
                </n-descriptions-item>
                <n-descriptions-item
                  v-if="job.state?.consecutiveErrors"
                  label="连续错误"
                >
                  {{ job.state.consecutiveErrors }}
                </n-descriptions-item>
              </n-descriptions>

              <section v-if="job.state?.lastError" class="detail-section">
                <h3 class="section-title">最近错误</h3>
                <n-alert type="error" :show-icon="false" class="error-alert">
                  {{ job.state.lastError }}
                </n-alert>
              </section>

              <section class="detail-section">
                <h3 class="section-title">Payload</h3>
                <pre class="json-block">{{ payloadJson }}</pre>
              </section>

              <section class="detail-section">
                <h3 class="section-title">投递配置</h3>
                <n-alert v-if="deliveryHint" type="info" :show-icon="false" class="info-alert">
                  {{ deliveryHint }}
                </n-alert>
                <pre class="json-block">{{ deliveryJson }}</pre>
              </section>
            </div>
          </n-tab-pane>

          <n-tab-pane name="history" tab="执行历史">
            <div class="tab-pane-inner">
              <TimerRunHistoryTable
                :entries="runs"
                :loading="runsLoading"
                :has-more="runsHasMore"
                show-filter
                @refresh="emit('refresh-runs')"
                @load-more="emit('load-more-runs')"
                @filter-change="emit('runs-filter', $event)"
              />
              <n-space v-if="lastRunId" class="poll-row">
                <n-button size="small" :loading="pollingRun" @click="emit('poll-run', lastRunId)">
                  查看最近手动运行结果
                </n-button>
              </n-space>
            </div>
          </n-tab-pane>

          <n-tab-pane name="edit" tab="编辑" :display-directive="'show'">
            <div class="tab-pane-inner tab-pane-form">
              <TimerJobForm
                v-if="activeTab === 'edit'"
                mode="edit"
                :job="job"
                :saving="saving"
                @cancel="activeTab = 'overview'"
                @submit="onFormSave"
              />
            </div>
          </n-tab-pane>
        </n-tabs>
      </div>

      <footer class="detail-footer">
        <n-button size="small" type="error" ghost @click="emit('delete')">删除任务</n-button>
      </footer>
    </template>

    <div v-else class="detail-empty">
      <n-empty description="从左侧选择一个定时任务">
        <template #icon>
          <n-icon size="48" :depth="3"><TimerOutline /></n-icon>
        </template>
      </n-empty>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NAlert, NButton, NDescriptions, NDescriptionsItem, NEmpty,
  NIcon, NTabPane, NTabs, NTag, NText, NSpace,
} from 'naive-ui';
import { PlayOutline, TimerOutline } from '@vicons/ionicons5';
import TimerRunHistoryTable from './TimerRunHistoryTable.vue';
import TimerJobForm from './TimerJobForm.vue';
import { scheduleSummary, statusTagType } from '../cronFormUtils';
import type { CronJob, CronRunEntry } from '@shared/types';

const props = defineProps<{
  job: CronJob | null;
  runs: CronRunEntry[];
  runsLoading?: boolean;
  runsHasMore?: boolean;
  saving?: boolean;
  running?: boolean;
  pollingRun?: boolean;
  lastRunId?: string | null;
}>();

const emit = defineEmits<{
  'refresh-runs': [];
  'load-more-runs': [];
  'runs-filter': [status: string];
  run: [];
  toggle: [];
  delete: [];
  save: [patch: Record<string, unknown>];
  'poll-run': [runId: string];
  'tab-change': [tab: string];
}>();

const message = useMessage();
const activeTab = ref('overview');

const statusLabel = computed(() => {
  const s = props.job?.status ?? 'idle';
  const map: Record<string, string> = {
    disabled: '已禁用', running: '运行中', ok: '正常', error: '异常', skipped: '跳过', idle: '空闲',
  };
  return map[s] ?? s;
});

const payloadJson = computed(() => JSON.stringify(props.job?.payload ?? {}, null, 2));
const deliveryJson = computed(() => JSON.stringify(props.job?.delivery ?? { mode: 'none' }, null, 2));

const deliveryHint = computed(() => {
  const d = props.job?.delivery;
  if (!d) return props.job?.sessionTarget === 'main' ? '主会话任务通常无 Runner 投递配置。' : null;
  if (d.channel === 'last') return 'channel: last — 投递目标将依赖会话上下文解析。';
  if (d.mode === 'webhook') return 'Webhook 模式：完成后 POST 到配置的 URL。';
  return null;
});

function formatTime(ms?: number) {
  if (!ms) return '—';
  return new Date(ms).toLocaleString('zh-CN');
}

function onTabChange(tab: string) {
  emit('tab-change', tab);
}

function onFormSave(patch: Record<string, unknown>) {
  emit('save', patch);
}

function copyId() {
  if (!props.job?.id) return;
  navigator.clipboard.writeText(props.job.id).then(
    () => message.success('已复制任务 ID'),
    () => message.warning('复制失败'),
  );
}
</script>

<style scoped>
.timer-detail {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--oc-timer-panel-bg);
  border: 1px solid var(--oc-timer-panel-border);
  border-radius: var(--oc-radius-lg);
  border-left: 3px solid color-mix(in srgb, var(--oc-accent) 55%, var(--oc-primary));
  color: var(--oc-timer-text);
  box-shadow: var(--oc-shadow-card);
}

.detail-header {
  flex-shrink: 0;
  padding: 16px 18px;
  border-bottom: 1px solid var(--oc-timer-panel-border);
  background: var(--oc-surface-elevated);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.detail-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--oc-timer-text);
  line-height: 1.3;
}

.detail-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--oc-timer-text-muted);
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.detail-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.detail-tabs-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-tabs :deep(.n-tabs-nav) {
  padding: 0 18px;
  background: color-mix(in srgb, var(--oc-surface-elevated) 50%, transparent);
}

.detail-tabs :deep(.n-tab-pane) {
  flex: 1;
  min-height: 0;
}

.tab-pane-inner {
  padding: 16px 18px 20px;
  overflow-y: auto;
  max-height: 100%;
}

.tab-pane-form {
  padding-bottom: 8px;
}

.detail-desc :deep(.n-descriptions-table-content__label) {
  color: var(--oc-timer-text-muted) !important;
}

.detail-desc :deep(.n-descriptions-table-content__content) {
  color: var(--oc-timer-text) !important;
}

.id-code {
  font-size: 12px;
}

.copy-btn {
  margin-left: 8px;
}

.detail-section {
  margin-top: 20px;
}

.section-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--oc-timer-text);
  letter-spacing: 0.02em;
}

.json-block {
  font-family: ui-monospace, 'Cascadia Code', 'SF Mono', Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  background: var(--oc-timer-code-bg);
  border: 1px solid var(--oc-timer-code-border);
  padding: 12px 14px;
  border-radius: var(--oc-radius-md);
  overflow: auto;
  max-height: 220px;
  margin: 0;
  color: var(--oc-timer-text);
}

.info-alert,
.error-alert {
  margin-bottom: 10px;
}

.poll-row {
  margin-top: 12px;
}

.detail-footer {
  flex-shrink: 0;
  padding: 12px 18px;
  border-top: 1px solid var(--oc-timer-panel-border);
  background: color-mix(in srgb, var(--oc-surface-elevated) 85%, transparent);
  backdrop-filter: blur(8px);
}

.detail-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.detail-empty :deep(.n-empty__description) {
  color: var(--oc-timer-text-muted);
}
</style>
