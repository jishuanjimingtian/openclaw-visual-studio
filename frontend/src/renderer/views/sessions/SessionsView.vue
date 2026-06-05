<template>
  <div class="page sessions-page">
    <HeaderToolbar>
      <n-text v-if="lastRefreshedLabel" depth="3" class="refresh-hint">{{ lastRefreshedLabel }}</n-text>
      <n-button :loading="store.loading" size="small" @click="refreshSessions">
        <template #icon><n-icon><refresh-outline /></n-icon></template>
        刷新
      </n-button>
      <n-button :loading="syncing" size="small" @click="syncToLocal">
        <template #icon><n-icon><cloud-download-outline /></n-icon></template>
        同步到本地
      </n-button>
      <n-button type="primary" size="small" @click="router.push('/chat')">
        <template #icon><n-icon><chatbox-outline /></n-icon></template>
        打开对话
      </n-button>
    </HeaderToolbar>

    <div class="page-body">
    <!-- 概览指标 -->
    <n-grid :cols="4" :x-gap="16" :y-gap="16" class="stats-grid">
      <n-grid-item v-for="stat in statsCards" :key="stat.label">
        <n-card size="small" class="stat-card" :bordered="true">
          <div class="stat-inner">
            <div class="stat-icon" :style="{ color: stat.color, background: stat.bg }">
              <n-icon size="20"><component :is="stat.icon" /></n-icon>
            </div>
            <div>
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <!-- Gateway 状态 -->
    <GatewayStatusBar
      :show-start-stop="false"
      :connection-hint="store.openclawMeta?.connectionHint ?? 'Gateway 未连接，无法加载会话。请确认 Gateway 已启动且后端 RPC 已握手。'"
    />
    <!-- 主区域 -->
    <div class="sessions-shell">
      <!-- 左侧列表 -->
      <aside class="session-panel">
        <div class="panel-toolbar">
          <n-input
            v-model:value="searchQuery"
            placeholder="搜索标题、Key、预览..."
            clearable
            size="small"
          >
            <template #prefix>
              <n-icon><search-outline /></n-icon>
            </template>
          </n-input>
          <n-tabs v-model:value="filterTab" type="segment" size="small" class="filter-tabs">
            <n-tab name="all">全部</n-tab>
            <n-tab name="running">运行中</n-tab>
            <n-tab name="today">今日</n-tab>
          </n-tabs>
          <n-select
            v-model:value="sortBy"
            size="small"
            :options="sortOptions"
            placeholder="排序"
          />
        </div>

        <div class="panel-body">
          <div v-if="store.loading && displayedSessions.length === 0" class="panel-loading">
            <n-skeleton v-for="i in 5" :key="i" text :repeat="2" />
          </div>

          <EmptyState
            v-else-if="!store.loading && displayedSessions.length === 0"
            :description="emptyListHint"
          >
            <template #actions>
              <n-button size="small" @click="refreshSessions">重新加载</n-button>
            </template>
          </EmptyState>

          <div v-else class="session-scroll">
            <button
              v-for="session in displayedSessions"
              :key="session.key"
              type="button"
              class="session-card"
              :class="{ active: selectedKey === session.key, running: session.hasActiveRun }"
              @click="selectSession(session)"
            >
              <div class="session-card-top">
                <span class="session-avatar">{{ sessionInitial(session) }}</span>
                <div class="session-card-main">
                  <div class="session-card-title-row">
                    <span class="session-card-title">{{ session.title }}</span>
                    <n-tag v-if="session.hasActiveRun" size="tiny" type="info" round>运行中</n-tag>
                  </div>
                  <p v-if="session.lastMessagePreview" class="session-card-preview">
                    {{ session.lastMessagePreview }}
                  </p>
                </div>
              </div>
              <div class="session-card-meta">
                <n-tag v-if="session.model" size="tiny" :bordered="false" round>
                  {{ shortModel(session.model) }}
                </n-tag>
                <n-tag v-if="session.channel" size="tiny" type="default" round>
                  {{ session.channel }}
                </n-tag>
                <span v-if="session.totalTokens" class="meta-text">{{ formatTokens(session.totalTokens) }}</span>
                <span v-if="session.updatedAt" class="meta-text">{{ formatTime(session.updatedAt) }}</span>
              </div>
            </button>
          </div>
        </div>

        <div class="panel-footer">
          <n-text depth="3">共 {{ displayedSessions.length }} / {{ store.openclawSessions.length }} 个会话</n-text>
        </div>
      </aside>

      <!-- 右侧详情 -->
      <main class="detail-panel">
        <template v-if="selectedSession">
          <header class="detail-header">
            <div class="detail-header-main">
              <h2 class="detail-title">{{ selectedSession.title }}</h2>
              <n-space :size="8" wrap class="detail-tags">
                <n-tag size="small" type="info">{{ selectedSession.model || '默认模型' }}</n-tag>
                <n-tag v-if="selectedSession.hasActiveRun" size="small" type="warning">Agent 运行中</n-tag>
                <n-tag v-if="sessionUsageLabel" size="small" :bordered="false">{{ sessionUsageLabel }}</n-tag>
              </n-space>
            </div>
            <n-space :size="8" wrap class="detail-actions">
              <n-button size="small" type="primary" @click="openInChat">
                <template #icon><n-icon><chatbox-outline /></n-icon></template>
                在对话中继续
              </n-button>
              <n-button size="small" quaternary :loading="store.historyLoading" @click="reloadHistory">
                刷新消息
              </n-button>
              <n-button
                v-if="store.historyPreview && !store.historyLoading"
                size="small"
                quaternary
                @click="loadFullHistory"
              >
                加载完整消息
              </n-button>
              <n-button size="small" quaternary @click="copySessionKey">
                复制 Key
              </n-button>
            </n-space>
          </header>

          <n-collapse v-model:expanded-names="metaExpanded" class="detail-meta">
            <n-collapse-item title="会话信息" name="meta">
              <n-descriptions :column="2" size="small" label-placement="left">
                <n-descriptions-item label="Session Key">
                  <n-text code>{{ selectedSession.key }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="Session ID">
                  {{ selectedSession.sessionId || '—' }}
                </n-descriptions-item>
                <n-descriptions-item label="类型">
                  {{ selectedSession.kind || '—' }}
                </n-descriptions-item>
                <n-descriptions-item label="渠道">
                  {{ selectedSession.channel || '—' }}
                </n-descriptions-item>
                <n-descriptions-item label="更新时间">
                  {{ selectedSession.updatedAt ? formatFullTime(selectedSession.updatedAt) : '—' }}
                </n-descriptions-item>
                <n-descriptions-item label="Token">
                  {{ selectedSession.totalTokens ? formatTokens(selectedSession.totalTokens) : '—' }}
                </n-descriptions-item>
              </n-descriptions>
            </n-collapse-item>
          </n-collapse>

          <div class="message-panel">
            <div v-if="store.historyLoading" class="message-state">
              <n-spin size="medium" />
              <n-text depth="3">加载消息中...</n-text>
            </div>
            <div v-else-if="visibleMessages.length === 0" class="message-state">
              <n-empty description="该会话暂无可见消息" size="small" />
            </div>
            <div v-else ref="messagesRef" class="message-list">
              <article
                v-for="(msg, idx) in visibleMessages"
                :key="idx"
                class="message-item"
                :class="[`role-${normalizeRole(msg.role)}`, { 'is-error': isErrorMsg(msg) }]"
              >
                <div class="message-avatar">{{ roleAvatar(msg.role) }}</div>
                <div class="message-body">
                  <div class="message-head">
                    <span class="message-role">{{ roleLabel(msg.role) }}</span>
                    <span v-if="msg.timestamp" class="message-time">{{ formatMsgTime(msg.timestamp) }}</span>
                  </div>
                  <div class="message-content">{{ msg.content }}</div>
                </div>
              </article>
            </div>
          </div>
        </template>

        <div v-else class="detail-empty">
          <n-empty description="从左侧选择一个会话查看详情">
            <template #extra>
              <n-button size="small" @click="refreshSessions">加载会话列表</n-button>
            </template>
          </n-empty>
        </div>
      </main>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  NInput, NButton, NTag, NSpace, NCard, NSkeleton, NText, NEmpty, NSpin,
  NGrid, NGridItem, NIcon, NTabs, NTab, NSelect, NCollapse, NCollapseItem,
  NDescriptions, NDescriptionsItem,
} from 'naive-ui';
import {
  RefreshOutline, CloudDownloadOutline, ChatboxOutline, SearchOutline,
  ChatbubblesOutline, FlashOutline, PulseOutline, RadioOutline,
} from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import GatewayStatusBar from '@/components/common/GatewayStatusBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import { useConversationStore } from '@/stores/conversation';
import { useOpenClawChatStore } from '@/stores/openclawChat';
import { openclawChatApi } from '@/api/openclawChat';
import { isHiddenChatContent } from '@/utils/chatHistory';
import { ocColors } from '@/utils/chartColors';
import type { OpenClawSession, OpenClawSessionUsage } from '@shared/types';

type FilterTab = 'all' | 'running' | 'today';
type SortKey = 'updated' | 'title' | 'tokens';

const message = useMessage();
const route = useRoute();
const router = useRouter();
const store = useConversationStore();
const chatStore = useOpenClawChatStore();

const SESSION_STORAGE_KEY = 'openclaw-sessions-selected-key';

const searchQuery = ref('');
const filterTab = ref<FilterTab>('all');
const sortBy = ref<SortKey>('updated');
const syncing = ref(false);
const selectedKey = ref<string | null>(null);
const messagesRef = ref<HTMLElement | null>(null);
const lastRefreshedAt = ref<Date | null>(null);
const sessionUsage = ref<OpenClawSessionUsage | null>(null);
const metaExpanded = ref<string[]>([]);

const sortOptions = [
  { label: '最近更新', value: 'updated' },
  { label: '标题 A-Z', value: 'title' },
  { label: 'Token 用量', value: 'tokens' },
];

const lastRefreshedLabel = computed(() => {
  if (!lastRefreshedAt.value) return '';
  return lastRefreshedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }) + ' 更新';
});

const activeRunCount = computed(
  () => store.openclawSessions.filter((s) => s.hasActiveRun).length,
);

const totalTokensSum = computed(() =>
  store.openclawSessions.reduce((sum, s) => sum + (s.totalTokens ?? 0), 0),
);

const statsCards = computed(() => [
  {
    label: '会话总数',
    value: store.openclawSessions.length,
    icon: ChatbubblesOutline,
    color: ocColors.primary,
    bg: 'color-mix(in srgb, var(--oc-primary) 12%, transparent)',
  },
  {
    label: '运行中',
    value: activeRunCount.value,
    icon: PulseOutline,
    color: ocColors.warning,
    bg: 'color-mix(in srgb, var(--oc-warning) 12%, transparent)',
  },
  {
    label: '会话 Token 合计',
    value: formatTokens(totalTokensSum.value),
    icon: FlashOutline,
    color: ocColors.metric,
    bg: 'color-mix(in srgb, var(--oc-metric) 12%, transparent)',
  },
  {
    label: 'Gateway',
    value: store.openclawMeta?.gatewayConnected ? '已连接' : '未连接',
    icon: RadioOutline,
    color: store.openclawMeta?.gatewayConnected ? ocColors.success : ocColors.error,
    bg: store.openclawMeta?.gatewayConnected
      ? 'color-mix(in srgb, var(--oc-success) 12%, transparent)'
      : 'color-mix(in srgb, var(--oc-error) 12%, transparent)',
  },
]);

const displayedSessions = computed(() => {
  let list = [...store.openclawSessions];
  const q = searchQuery.value.trim().toLowerCase();

  if (filterTab.value === 'running') {
    list = list.filter((s) => s.hasActiveRun);
  } else if (filterTab.value === 'today') {
    const today = new Date().toDateString();
    list = list.filter((s) => s.updatedAt && new Date(s.updatedAt).toDateString() === today);
  }

  if (q) {
    list = list.filter(
      (s) =>
        s.title.toLowerCase().includes(q) ||
        s.key.toLowerCase().includes(q) ||
        (s.lastMessagePreview?.toLowerCase().includes(q) ?? false) ||
        (s.model?.toLowerCase().includes(q) ?? false),
    );
  }

  list.sort((a, b) => {
    if (sortBy.value === 'title') {
      return a.title.localeCompare(b.title, 'zh-CN');
    }
    if (sortBy.value === 'tokens') {
      return (b.totalTokens ?? 0) - (a.totalTokens ?? 0);
    }
    return (b.updatedAt ?? 0) - (a.updatedAt ?? 0);
  });

  return list;
});

const selectedSession = computed(
  () => store.openclawSessions.find((s) => s.key === selectedKey.value) ?? null,
);

const visibleMessages = computed(() =>
  store.chatMessages.filter((m) => !isHiddenChatContent(m.content)),
);

const sessionUsageLabel = computed(() => {
  if (sessionUsage.value?.fromGateway && sessionUsage.value.totalTokens > 0) {
    return `${formatTokens(sessionUsage.value.totalTokens)} tok`;
  }
  if (selectedSession.value?.totalTokens) {
    return `${formatTokens(selectedSession.value.totalTokens)} tok`;
  }
  return '';
});

const emptyListHint = computed(() => {
  if (!store.openclawMeta?.gatewayConnected) {
    return 'Gateway 未连接，请先启动 Gateway';
  }
  if (filterTab.value !== 'all' || searchQuery.value.trim()) {
    return '没有符合筛选条件的会话';
  }
  return '暂无 OpenClaw 会话，请先在对话页或 CLI 中创建';
});

function normalizeRole(role: string) {
  const r = role.toLowerCase();
  if (r === 'human') return 'user';
  return r;
}

function isErrorMsg(msg: { role: string; content: string }) {
  return (
    normalizeRole(msg.role) === 'assistant' &&
    (/^Agent failed/i.test(msg.content) || msg.content.startsWith('⚠️'))
  );
}

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

function formatTime(ts: number) {
  const d = new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatFullTime(ts: number) {
  return new Date(ts).toLocaleString('zh-CN');
}

function formatMsgTime(ts: number) {
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function shortModel(model: string) {
  const parts = model.split('/');
  return parts.length > 1 ? parts[parts.length - 1] : model;
}

function sessionInitial(session: OpenClawSession) {
  const t = session.title?.trim();
  return t ? t.charAt(0).toUpperCase() : '?';
}

function roleLabel(role: string) {
  const map: Record<string, string> = {
    user: '你',
    assistant: '助手',
    system: '系统',
    tool: '工具',
  };
  return map[normalizeRole(role)] || role;
}

function roleAvatar(role: string) {
  const map: Record<string, string> = {
    user: '我',
    assistant: 'AI',
    system: 'S',
    tool: 'T',
  };
  return map[normalizeRole(role)] || '?';
}

async function fetchSessionUsage(key: string) {
  sessionUsage.value = null;
  if (!store.openclawMeta?.gatewayConnected) return;
  try {
    const res = await openclawChatApi.getSessionUsage(key);
    sessionUsage.value = res.data;
  } catch {
    // optional
  }
}

async function refreshSessions() {
  try {
    await store.fetchOpenClawSessions(searchQuery.value || undefined);
    lastRefreshedAt.value = new Date();

    const highlight = route.query.highlight as string | undefined;
    if (highlight) {
      const matched =
        store.openclawSessions.find((s) => s.key === highlight) ??
        store.openclawSessions.find((s) => s.sessionId === highlight);
      if (matched) {
        selectSession(matched);
        return;
      }
    }

    const savedKey = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (savedKey) {
      const saved = store.openclawSessions.find((s) => s.key === savedKey);
      if (saved) {
        selectSession(saved);
        return;
      }
    }

    if (selectedKey.value && store.openclawSessions.some((s) => s.key === selectedKey.value)) {
      return;
    }

    selectedKey.value = null;
  } catch {
    message.error('加载会话失败');
  }
}

async function syncToLocal() {
  syncing.value = true;
  try {
    await store.syncFromOpenClaw();
    message.success('已同步到本地数据库');
    lastRefreshedAt.value = new Date();
  } catch {
    message.error('同步失败');
  } finally {
    syncing.value = false;
  }
}

function selectSession(session: OpenClawSession) {
  selectedKey.value = session.key;
  sessionStorage.setItem(SESSION_STORAGE_KEY, session.key);
  store.selectSession(session, true);
  window.setTimeout(() => fetchSessionUsage(session.key), 0);
}

function reloadHistory() {
  if (selectedSession.value) {
    store.selectSession(selectedSession.value, store.historyPreview);
    void fetchSessionUsage(selectedSession.value.key);
  }
}

async function loadFullHistory() {
  if (!selectedSession.value) return;
  await store.fetchFullSessionHistory(selectedSession.value.key);
  scrollMessagesToBottom();
}

function openInChat() {
  if (!selectedSession.value) return;
  chatStore.selectSession(selectedSession.value);
  router.push('/chat');
}

async function copySessionKey() {
  if (!selectedSession.value) return;
  try {
    await navigator.clipboard.writeText(selectedSession.value.key);
    message.success('Session Key 已复制');
  } catch {
    message.error('复制失败');
  }
}

function scrollMessagesToBottom() {
  nextTick(() => {
    const el = messagesRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

onMounted(() => {
  refreshSessions();
});

watch(
  () => [store.historyLoading, visibleMessages.value.length],
  ([loading], [wasLoading]) => {
    if (wasLoading && !loading) {
      scrollMessagesToBottom();
    }
  },
);
</script>

<style scoped>
.sessions-page {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sessions-page :deep(.page-header) {
  flex-shrink: 0;
}

.refresh-hint {
  font-size: 12px;
}

.stats-grid {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.stat-card {
  border-left: 3px solid var(--oc-primary);
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--oc-session-text);
}

.stat-label {
  font-size: 12px;
  color: var(--oc-session-text-muted);
  margin-top: 2px;
}

.sessions-page .page-body {
  display: flex;
  flex-direction: column;
}

.sessions-shell {
  display: grid;
  grid-template-columns: minmax(0, 360px) minmax(0, 1fr);
  gap: 16px;
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  width: 100%;
  overflow: hidden;
}

.session-panel,
.detail-panel {
  min-height: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--oc-session-panel-bg);
  border: 1px solid var(--oc-session-panel-border);
  border-radius: 12px;
  color: var(--oc-session-text);
}

.session-panel {
  border-left: 3px solid var(--oc-primary);
}

.panel-toolbar {
  flex-shrink: 0;
  padding: 14px 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid var(--oc-session-panel-border);
  background: var(--oc-surface-elevated);
}

.filter-tabs {
  width: 100%;
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-loading {
  padding: 16px;
}

.session-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px;
}

.session-card {
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 8px;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
  color: inherit;
  font: inherit;
}

.session-card:hover {
  background: var(--oc-session-card-hover);
}

.session-card.active {
  background: var(--oc-session-card-active);
  border-color: var(--oc-primary);
  box-shadow: inset 3px 0 0 var(--oc-primary);
}

.session-card.running:not(.active) {
  border-color: color-mix(in srgb, var(--oc-warning) 40%, transparent);
}

.session-card-top {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.session-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--oc-primary), var(--oc-accent-soft, var(--oc-accent)));
  color: var(--oc-session-avatar-fg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.session-card-main {
  min-width: 0;
  flex: 1;
}

.session-card-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.session-card-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--oc-session-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.session-card-preview {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--oc-session-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-left: 46px;
}

.meta-text {
  font-size: 11px;
  color: var(--oc-session-text-muted);
}

.panel-footer {
  flex-shrink: 0;
  padding: 10px 14px;
  border-top: 1px solid var(--oc-session-panel-border);
  font-size: 12px;
  color: var(--oc-session-text-muted);
  background: var(--oc-surface-elevated);
}

.detail-panel {
  border-left: 3px solid color-mix(in srgb, var(--oc-accent) 60%, var(--oc-primary));
}

.detail-header {
  flex-shrink: 0;
  padding: 16px 18px;
  border-bottom: 1px solid var(--oc-session-panel-border);
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
  color: var(--oc-session-text);
}

.detail-tags {
  margin-top: 8px;
}

.detail-meta {
  flex-shrink: 0;
  margin: 0;
  border-bottom: 1px solid var(--oc-session-panel-border);
}

.detail-meta :deep(.n-collapse-item__header) {
  padding: 10px 18px;
  font-size: 13px;
  color: var(--oc-session-text-secondary) !important;
}

.detail-meta :deep(.n-descriptions-table-content),
.detail-meta :deep(.n-descriptions-table-content__label) {
  color: var(--oc-session-text-secondary) !important;
}

.detail-meta :deep(.n-descriptions-table-content__content) {
  color: var(--oc-session-text) !important;
}

.detail-meta :deep(.n-collapse-item__content-inner) {
  padding: 0 18px 14px;
}

.message-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-state,
.detail-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message-item {
  display: flex;
  gap: 10px;
  max-width: 92%;
}

.message-item.role-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-item.role-user .message-body {
  align-items: flex-end;
}

.message-item.role-user .message-content {
  background: var(--oc-session-msg-user-bg);
  border: 1px solid var(--oc-session-msg-user-border);
  color: var(--oc-session-msg-text);
}

.message-item.role-assistant .message-content {
  background: var(--oc-session-msg-assistant-bg);
  border: 1px solid var(--oc-session-msg-assistant-border);
  color: var(--oc-session-msg-text);
}

.message-item.role-system .message-content {
  background: color-mix(in srgb, var(--oc-warning) 16%, var(--oc-session-msg-assistant-bg));
  border: 1px solid color-mix(in srgb, var(--oc-warning) 35%, transparent);
  color: var(--oc-session-msg-text);
  font-size: 13px;
}

.message-item.is-error .message-content {
  background: color-mix(in srgb, var(--oc-error) 14%, var(--oc-session-msg-assistant-bg));
  border: 1px solid color-mix(in srgb, var(--oc-error) 40%, transparent);
  color: var(--oc-session-msg-text);
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--oc-session-msg-assistant-bg);
  border: 1px solid var(--oc-session-msg-assistant-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--oc-session-text-secondary);
  flex-shrink: 0;
}

.message-item.role-user .message-avatar {
  background: var(--oc-primary);
  border-color: transparent;
  color: var(--oc-session-avatar-fg);
}

.message-item.role-assistant .message-avatar {
  background: var(--oc-session-avatar-assistant-bg);
  border-color: transparent;
  color: var(--oc-session-avatar-assistant-fg);
}

.message-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.message-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.message-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--oc-session-text-secondary);
}

.message-time {
  font-size: 11px;
  color: var(--oc-session-text-muted);
}

.message-content {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--oc-session-msg-text);
}

/* Naive 组件在会话区域内的可读性（限定作用域，避免影响顶栏按钮） */
.session-panel :deep(.n-text),
.detail-panel :deep(.n-text),
.gateway-strip :deep(.n-text),
.panel-footer :deep(.n-text),
.message-state :deep(.n-text),
.detail-empty :deep(.n-text) {
  color: var(--oc-session-text-secondary);
}

.session-panel :deep(.n-text.n-text--strong),
.detail-panel :deep(.n-text.n-text--strong),
.gateway-strip :deep(.n-text.n-text--strong) {
  color: var(--oc-session-text) !important;
}

.session-panel :deep(.n-empty .n-empty__description),
.detail-panel :deep(.n-empty .n-empty__description),
.detail-empty :deep(.n-empty .n-empty__description) {
  color: var(--oc-session-text-muted);
}

.panel-toolbar :deep(.n-tabs .n-tabs-tab) {
  color: var(--oc-session-text-muted);
}

.panel-toolbar :deep(.n-tabs .n-tabs-tab--active) {
  color: var(--oc-session-text) !important;
}

@media (max-width: 960px) {
  .sessions-page {
    overflow: visible;
  }

  .sessions-shell {
    grid-template-columns: 1fr;
    flex: none;
    overflow: visible;
  }

  .session-panel,
  .detail-panel {
    height: auto;
    max-height: none;
  }

  .session-scroll,
  .message-list {
    max-height: 45vh;
  }
}
</style>
