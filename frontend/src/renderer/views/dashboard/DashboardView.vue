<template>
  <div class="dashboard page dashboard-page">
    <HeaderToolbar>
      <n-text v-if="gatewayStore.lastRefreshedLabel" class="refresh-hint">
        {{ gatewayStore.lastRefreshedLabel }} 更新
      </n-text>
      <n-button :loading="pageRefreshing" size="small" @click="refresh(true)">
        <template #icon><n-icon><refresh-outline /></n-icon></template>
        刷新
      </n-button>
    </HeaderToolbar>

    <div class="page-body">
    <div class="dashboard-pinned">
      <GatewayStatusBar
        :subtitle="gatewaySubtitle"
        @start="startGateway"
        @stop="stopGateway"
      >
        <template #actions>
          <n-button size="small" @click="$router.push('/chat')">打开对话</n-button>
          <n-button size="small" @click="$router.push('/deployment')">OpenClaw 部署</n-button>
        </template>
      </GatewayStatusBar>

      <div v-if="store.primaryModelRef || store.openclawConfigPath" class="model-info-strip">
        <n-text v-if="store.primaryModelRef" class="model-info-text">
          主模型 <n-text strong class="model-info-strong">{{ store.primaryModelRef }}</n-text>
        </n-text>
        <n-text v-if="store.openclawConfigPath" class="config-path">
          配置 {{ store.openclawConfigPath }}
        </n-text>
      </div>

      <section class="dashboard-kpi-row">
        <DashboardStatCard
          v-for="card in businessCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :sub="card.sub"
          :icon="card.icon"
          :tone="card.tone"
          :accent="card.color"
          :hero="card.hero"
          :on-click="card.onClick"
        />
      </section>

      <DashboardHealthPanel :items="healthRings" />
    </div>

    <div class="dashboard-bottom">
        <n-card title="最近会话" size="small" class="dashboard-pair-card recent-sessions-card">
          <template #header-extra>
            <n-button text type="primary" @click="$router.push('/sessions')">查看全部</n-button>
          </template>
          <div class="dashboard-card-scroll recent-sessions-body">
            <div
              v-if="store.loadingRecent && recentConversations.length === 0"
              class="recent-session-loading"
            >
              <n-spin size="small" />
            </div>
            <div
              v-else-if="recentConversations.length > 0"
              class="recent-session-panel"
            >
              <ul class="recent-session-list" role="list">
                <li
                  v-for="session in recentConversations"
                  :key="session.id"
                  role="listitem"
                  class="recent-session-row"
                  :class="{ 'is-active': sessionHasMessages(session) }"
                  tabindex="0"
                  @click="openSession(session.id)"
                  @keydown.enter="openSession(session.id)"
                >
                  <span class="recent-session-row__accent" aria-hidden="true" />
                  <div class="recent-session-row__content">
                    <div class="recent-session-row__head">
                      <span class="recent-session-title">{{ formatSessionTitle(session.title) }}</span>
                      <n-tag size="small" :bordered="false" round class="recent-session-tag">
                        {{ session.model }}
                      </n-tag>
                    </div>
                    <p class="recent-session-meta">{{ formatSessionMeta(session) }}</p>
                  </div>
                </li>
              </ul>
            </div>
            <div v-else class="recent-session-empty">
              <n-empty description="暂无会话，去对话页开始吧" size="small">
                <template #extra>
                  <n-button type="primary" size="small" @click="$router.push('/chat')">打开对话</n-button>
                </template>
              </n-empty>
            </div>
          </div>
        </n-card>

        <n-card title="快速操作" size="small" class="dashboard-pair-card quick-actions-card">
          <div class="dashboard-card-scroll quick-actions-body">
            <div class="quick-action-panel">
              <button
                v-for="action in quickActions"
                :key="action.path"
                type="button"
                class="quick-action-row"
                :style="{ '--action-accent': action.color }"
                @click="$router.push(action.path)"
              >
                <span class="quick-action-row__accent" aria-hidden="true" />
                <span class="quick-action-row__icon">
                  <n-icon size="18"><component :is="action.icon" /></n-icon>
                </span>
                <span class="quick-action-row__label">{{ action.label }}</span>
                <n-icon class="quick-action-row__chevron" size="16"><chevron-forward-outline /></n-icon>
              </button>
            </div>
          </div>
        </n-card>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'DashboardView' });

import { ref, computed, onMounted, onActivated, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  NCard, NIcon, NButton, NTag,
  NEmpty, NText, NSpin,
} from 'naive-ui';
import {
  ChatbubblesOutline, PulseOutline, BarChartOutline,
  CloudDownloadOutline, HardwareChipOutline, StorefrontOutline, RefreshOutline,
  CubeOutline, ChevronForwardOutline,
} from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import GatewayStatusBar from '@/components/common/GatewayStatusBar.vue';
import DashboardStatCard from '@/components/dashboard/DashboardStatCard.vue';
import DashboardHealthPanel from '@/components/dashboard/DashboardHealthPanel.vue';
import { useDashboardStore } from '@/stores/dashboard';
import { useGatewayStore } from '@/stores/gateway';
import type { DashboardRecentConversation } from '@shared/types';
import { ocColors, progressColor } from '@/utils/chartColors';

const router = useRouter();
const message = useMessage();
const store = useDashboardStore();
const gatewayStore = useGatewayStore();

const pageRefreshing = ref(false);
const gatewayLoading = ref(false);
let statsPollTimer: ReturnType<typeof setInterval> | null = null;
let tokenPollTimer: ReturnType<typeof setInterval> | null = null;
let lastPageRefreshAt = 0;

const recentConversations = computed(() =>
  [...store.recentConversations].sort(compareRecentSessions),
);

const gatewaySubtitle = computed(() => {
  if (gatewayStore.endpoint) return gatewayStore.endpoint;
  if (gatewayStore.port) return `端口 ${gatewayStore.port}`;
  return '未检测到运行中的 Gateway';
});

const cpuColor = computed(() => progressColor(store.metrics.cpu, 80, 50));
const memColor = computed(() => progressColor(store.metrics.memory, 85, 60));
const diskColor = computed(() => ocColors.metric);

const businessCards = computed(() => [
  {
    label: '会话总数',
    value: store.stats.totalConversations,
    sub: `今日消息 ${store.stats.messagesToday}`,
    icon: ChatbubblesOutline,
    tone: 'primary' as const,
    color: ocColors.primary,
    onClick: () => router.push('/sessions'),
  },
  {
    label: '已启用模型',
    value: `${store.stats.activeModels}/${store.stats.totalModels}`,
    sub: store.primaryModelRef ? `主模型 ${store.primaryModelRef}` : undefined,
    icon: HardwareChipOutline,
    tone: 'success' as const,
    color: ocColors.success,
    onClick: () => router.push('/models'),
  },
  {
    label: '已安装 Skill',
    value: store.stats.installedSkills,
    sub: `市场记录 ${store.stats.totalSkills}`,
    icon: CubeOutline,
    tone: 'warning' as const,
    color: ocColors.warning,
    onClick: () => router.push('/marketplace'),
  },
  {
    label: '今日 Token',
    value: store.loadingTokens ? '…' : formatTokens(store.metrics.tokenUsage),
    sub: store.loadingTokens
      ? '正在从 Gateway 拉取…'
      : gatewayStore.wsConnected
        ? 'Gateway 按日汇总'
        : `累计消息 ${store.stats.totalMessages}`,
    icon: BarChartOutline,
    tone: 'accent' as const,
    color: ocColors.accent,
    hero: true,
    onClick: () => router.push('/analytics'),
  },
]);

const activeSessionPct = computed(() => {
  const total = store.stats.totalConversations;
  if (total <= 0) return 0;
  return Math.min(100, Math.round((store.metrics.sessionCount / total) * 100));
});

const healthRings = computed(() => [
  {
    label: 'CPU',
    display: `${store.metrics.cpu}%`,
    percentage: store.metrics.cpu,
    color: cpuColor.value,
    onClick: () => router.push('/monitor'),
  },
  {
    label: '内存',
    display: `${store.metrics.memory}%`,
    percentage: store.metrics.memory,
    color: memColor.value,
    onClick: () => router.push('/monitor'),
  },
  {
    label: '磁盘',
    display: `${store.metrics.disk}%`,
    percentage: store.metrics.disk,
    color: diskColor.value,
    onClick: () => router.push('/monitor'),
  },
  {
    label: '活跃会话',
    display: String(store.metrics.sessionCount),
    percentage: activeSessionPct.value,
    color: ocColors.primary,
    onClick: () => router.push('/sessions'),
  },
]);

const quickActions = [
  { label: 'OpenClaw 对话', path: '/chat', icon: ChatbubblesOutline, color: ocColors.primary },
  { label: '一键部署', path: '/deployment', icon: CloudDownloadOutline, color: ocColors.accent },
  { label: '模型管理', path: '/models', icon: HardwareChipOutline, color: ocColors.success },
  { label: 'Skill 市场', path: '/marketplace', icon: StorefrontOutline, color: ocColors.warning },
  { label: '系统监控', path: '/monitor', icon: PulseOutline, color: ocColors.metric },
];

function formatTokens(tokens: number): string {
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(1)}M`;
  if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(1)}K`;
  return String(tokens);
}

function formatTime(iso: string): string {
  if (!iso) return '—';
  try {
    return new Date(iso.replace(' ', 'T')).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

function parseSessionTime(iso?: string | null): number {
  if (!iso) return 0;
  const ts = Date.parse(iso.replace(' ', 'T'));
  return Number.isNaN(ts) ? 0 : ts;
}

function sessionHasMessages(session: DashboardRecentConversation): boolean {
  return session.messageCount > 0 || Boolean(session.lastMessagePreview?.trim());
}

function compareRecentSessions(a: DashboardRecentConversation, b: DashboardRecentConversation): number {
  const hasMsgDiff = Number(sessionHasMessages(b)) - Number(sessionHasMessages(a));
  if (hasMsgDiff !== 0) return hasMsgDiff;
  return parseSessionTime(b.updatedAt) - parseSessionTime(a.updatedAt);
}

function flattenPreviewText(text: string): string {
  return text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]+`/g, ' ')
    .replace(/\|[^|\n]+\|/g, ' ')
    .replace(/[#>*\-_~[\]()!]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function truncateText(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen)}…`;
}

function formatSessionTitle(title: string): string {
  if (!title?.trim()) return '未命名会话';
  return truncateText(flattenPreviewText(title), 42);
}

function formatSessionPreview(preview?: string | null): string | null {
  if (!preview?.trim()) return null;
  const flat = flattenPreviewText(preview);
  if (!flat) return null;
  return truncateText(flat, 64);
}

function formatSessionMeta(session: DashboardRecentConversation): string {
  const preview = formatSessionPreview(session.lastMessagePreview);
  const time = formatTime(session.updatedAt);
  if (preview) return `${preview} · ${time}`;
  return `${session.messageCount} 条消息 · ${time}`;
}

function openSession(id: string) {
  router.push({ path: '/sessions', query: { highlight: id } });
}

async function refresh(force = false) {
  pageRefreshing.value = true;
  try {
    await Promise.allSettled([
      force ? store.fetchAllProgressive() : store.refreshLight(),
      gatewayStore.syncStatus(force),
    ]);
  } finally {
    pageRefreshing.value = false;
    lastPageRefreshAt = Date.now();
  }
}

async function initDashboard() {
  store.startLocalMetricsPolling(3000);
  pageRefreshing.value = true;
  try {
    await Promise.allSettled([
      store.fetchAllProgressive(),
      gatewayStore.syncStatus(false),
    ]);
  } finally {
    pageRefreshing.value = false;
    lastPageRefreshAt = Date.now();
  }
}

async function startGateway() {
  gatewayLoading.value = true;
  try {
    await gatewayStore.startAndPoll(gatewayStore.port);
    if (gatewayStore.isHealthy) {
      message.success('Gateway 已就绪');
    } else if (gatewayStore.isStarting || gatewayStore.wsConnected) {
      message.warning('Gateway 仍在启动中，请稍候或点击刷新查看状态');
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '启动失败');
  } finally {
    gatewayLoading.value = false;
  }
}

async function stopGateway() {
  gatewayLoading.value = true;
  try {
    await gatewayStore.stopAndHalt();
    message.success('Gateway 已停止');
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '停止失败');
  } finally {
    gatewayLoading.value = false;
  }
}

onMounted(() => {
  void initDashboard();
  statsPollTimer = setInterval(() => {
    store.refreshLight();
  }, 20000);
  tokenPollTimer = setInterval(() => {
    store.fetchTodayTokens();
  }, 60000);
});

onActivated(async () => {
  await gatewayStore.syncStatus(false);
  if (Date.now() - lastPageRefreshAt > 30_000) {
    await refresh(false);
  }
});

onUnmounted(() => {
  store.stopLocalMetricsPolling();
  if (statsPollTimer) clearInterval(statsPollTimer);
  if (tokenPollTimer) clearInterval(tokenPollTimer);
});
</script>

<style scoped>
.dashboard-page {
  --dashboard-text: var(--oc-session-text);
  --dashboard-text-secondary: var(--oc-session-text-secondary);
  --dashboard-text-muted: var(--oc-session-text-muted);
  --dashboard-heading: var(--oc-config-heading, var(--oc-session-text));
  color: var(--dashboard-text);
  flex: 1 1 0;
  min-height: 0;
}

.dashboard-page .page-body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 0;
}

.dashboard-pinned {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 14px;
}

.dashboard-bottom {
  flex: 1 1 0;
  min-height: 220px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
  overflow: hidden;
}

@media (max-width: 900px) {
  .dashboard-bottom {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }
}

.dashboard-kpi-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1100px) {
  .dashboard-kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.model-info-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--oc-primary) 5%, transparent);
  font-size: 13px;
}

.model-info-text {
  color: var(--dashboard-text-secondary);
}

.model-info-strong {
  color: var(--dashboard-text) !important;
}

.config-path {
  font-size: 12px;
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--dashboard-text-muted);
}

.refresh-hint {
  font-size: 12px;
  margin-right: 8px;
  color: var(--dashboard-text-muted);
}

.dashboard {
  --recent-session-row-h: 56px;
}

.dashboard :deep(.n-card) {
  border-radius: var(--oc-radius-lg);
}

.dashboard :deep(.n-card-header) {
  font-weight: 600;
}

.dashboard :deep(.n-card-header__main) {
  color: var(--dashboard-heading);
}

.dashboard :deep(.n-card-header__extra) {
  color: var(--dashboard-text-secondary);
}

.dashboard :deep(.n-empty .n-empty__description) {
  color: var(--dashboard-text-muted);
}

.dashboard-pair-card {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  display: flex !important;
  flex-direction: column;
  background: var(--oc-stat-bg) !important;
  border: 1px solid var(--oc-stat-border) !important;
  box-shadow: var(--oc-shadow-card);
}

.dashboard-pair-card :deep(.n-card-header) {
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.dashboard-pair-card :deep(.n-card-header__main) {
  color: var(--dashboard-heading) !important;
  font-weight: 600;
}

.dashboard-pair-card :deep(.n-card-content) {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.dashboard-card-scroll {
  position: absolute;
  inset: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--oc-primary) 45%, transparent) transparent;
  padding-bottom: 12px;
  box-sizing: border-box;
}

.dashboard-card-scroll::-webkit-scrollbar {
  width: 6px;
}

.dashboard-card-scroll::-webkit-scrollbar-track {
  background: color-mix(in srgb, var(--oc-primary) 6%, transparent);
  border-radius: 3px;
}

.dashboard-card-scroll::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: color-mix(in srgb, var(--oc-primary) 45%, transparent);
}

.dashboard-card-scroll::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--oc-primary) 62%, transparent);
}

.recent-sessions-body,
.quick-actions-body {
  box-sizing: border-box;
}

.quick-actions-card :deep(.n-card-content) {
  padding-top: 0 !important;
}

.quick-actions-body {
  padding-top: 0;
}

.quick-action-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid var(--oc-stat-border);
  border-radius: var(--oc-radius-md);
  background: color-mix(in srgb, var(--oc-surface-elevated) 88%, transparent);
  padding: 4px;
}

.quick-action-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  padding: 8px 10px 8px 6px;
  border: none;
  border-radius: calc(var(--oc-radius-md) - 2px);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    background-color var(--oc-transition),
    box-shadow var(--oc-transition),
    transform 0.15s ease;
}

.quick-action-row:hover,
.quick-action-row:focus-visible {
  background: color-mix(in srgb, var(--action-accent, var(--oc-primary)) 8%, transparent);
  outline: none;
}

.quick-action-row:active {
  transform: scale(0.99);
}

.quick-action-row__accent {
  flex-shrink: 0;
  width: 3px;
  align-self: stretch;
  margin: 4px 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--action-accent, var(--oc-primary)) 35%, transparent);
  transition: background var(--oc-transition);
}

.quick-action-row:hover .quick-action-row__accent,
.quick-action-row:focus-visible .quick-action-row__accent {
  background: linear-gradient(
    180deg,
    var(--action-accent, var(--oc-primary)) 0%,
    color-mix(in srgb, var(--action-accent, var(--oc-primary)) 55%, var(--oc-accent)) 100%
  );
}

.quick-action-row__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--action-accent, var(--oc-primary));
  background: color-mix(in srgb, var(--action-accent, var(--oc-primary)) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--action-accent, var(--oc-primary)) 24%, transparent);
  transition:
    background-color var(--oc-transition),
    border-color var(--oc-transition),
    transform 0.15s ease;
}

.quick-action-row:hover .quick-action-row__icon,
.quick-action-row:focus-visible .quick-action-row__icon {
  background: color-mix(in srgb, var(--action-accent, var(--oc-primary)) 20%, transparent);
  border-color: color-mix(in srgb, var(--action-accent, var(--oc-primary)) 38%, transparent);
  transform: scale(1.04);
}

.quick-action-row__label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--dashboard-text);
  line-height: 1.35;
}

.quick-action-row__chevron {
  flex-shrink: 0;
  color: var(--dashboard-text-muted);
  opacity: 0.45;
  transition:
    color var(--oc-transition),
    opacity var(--oc-transition),
    transform 0.15s ease;
}

.quick-action-row:hover .quick-action-row__chevron,
.quick-action-row:focus-visible .quick-action-row__chevron {
  color: var(--action-accent, var(--oc-primary));
  opacity: 1;
  transform: translateX(2px);
}

.recent-sessions-card :deep(.n-card-content) {
  padding-top: 0 !important;
}

.recent-sessions-card .dashboard-card-scroll {
  padding-top: 0;
}

.recent-session-loading,
.recent-session-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recent-session-panel {
  flex: 0 0 auto;
  border: 1px solid var(--oc-stat-border);
  border-radius: var(--oc-radius-md);
  background: color-mix(in srgb, var(--oc-surface-elevated) 88%, transparent);
  padding: 4px;
}

.recent-session-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.recent-session-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  height: var(--recent-session-row-h);
  box-sizing: border-box;
  padding: 0 6px 0 0;
  border-radius: calc(var(--oc-radius-md) - 2px);
  cursor: pointer;
  transition:
    background-color var(--oc-transition),
    box-shadow var(--oc-transition);
}

.recent-session-row + .recent-session-row {
  margin-top: 2px;
}

.recent-session-row:hover,
.recent-session-row:focus-visible {
  background: var(--oc-session-card-hover);
  outline: none;
}

.recent-session-row.is-active:hover,
.recent-session-row.is-active:focus-visible {
  background: var(--oc-session-card-active);
}

.recent-session-row__accent {
  flex-shrink: 0;
  width: 3px;
  margin: 8px 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--oc-muted) 25%, transparent);
  transition: background var(--oc-transition);
}

.recent-session-row.is-active .recent-session-row__accent {
  background: linear-gradient(
    180deg,
    var(--oc-primary) 0%,
    color-mix(in srgb, var(--oc-accent) 70%, var(--oc-primary)) 100%
  );
}

.recent-session-row__content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 6px 4px 6px 0;
}

.recent-session-row__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.recent-session-tag {
  flex-shrink: 0;
  max-width: 46%;
  background: color-mix(in srgb, var(--oc-primary) 12%, transparent) !important;
  color: var(--oc-primary-hover) !important;
}

[data-theme='dark'] .recent-session-tag {
  color: var(--oc-primary) !important;
}

.recent-session-tag :deep(.n-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}

.recent-session-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  font-size: 13px;
  line-height: 1.35;
  color: var(--dashboard-text);
}

.recent-session-meta {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--dashboard-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.35;
}
</style>
