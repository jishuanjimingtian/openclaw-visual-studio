<template>
  <div class="page chat-page">
    <div class="page-body">
    <div class="chat-layout">
      <!-- 左侧会话列表 -->
      <n-card class="chat-sidebar" size="small" :bordered="true">
        <template #header>
          <n-space justify="space-between" align="center" style="width: 100%">
            <n-text strong>会话</n-text>
            <n-tag :type="store.gatewayConnected ? 'success' : 'warning'" size="tiny" round>
              {{ store.gatewayConnected ? '在线' : '离线' }}
            </n-tag>
          </n-space>
        </template>

        <n-space vertical :size="10" class="sidebar-tools">
          <n-input
            v-model:value="sessionSearch"
            placeholder="搜索会话..."
            clearable
            size="small"
          />
          <n-button
            block
            size="small"
            :loading="store.loadingSessions"
            @click="refreshSessions"
          >
            刷新列表
          </n-button>
        </n-space>

        <div v-if="!store.gatewayConnected" class="sidebar-hint">
          <n-text depth="3" style="font-size: 12px">
            Gateway 未连接。请启动 <code>openclaw gateway</code> 后刷新。
          </n-text>
        </div>

        <div class="sidebar-body">
          <div v-if="store.loadingSessions && filteredSessions.length === 0" class="sidebar-loading">
            <n-skeleton v-for="i in 5" :key="i" text :repeat="1" />
          </div>
          <n-empty
            v-else-if="filteredSessions.length === 0"
            size="small"
            :description="emptySessionHint"
            style="margin-top: 24px"
          />
          <div v-else class="session-scroll">
            <div class="session-list">
              <button
                v-for="s in filteredSessions"
                :key="s.key"
                type="button"
                class="session-item"
                :class="{ active: store.sessionKey === s.key }"
                @click="store.selectSession(s)"
              >
                <div class="session-item-top">
                  <span class="session-title">{{ s.title }}</span>
                  <n-tag v-if="s.hasActiveRun" size="tiny" type="info">运行中</n-tag>
                </div>
                <p v-if="s.lastMessagePreview" class="session-preview">
                  {{ s.lastMessagePreview }}
                </p>
                <div class="session-meta">
                  <span>{{ s.model || '默认模型' }}</span>
                  <span v-if="s.totalTokens">{{ formatTokens(s.totalTokens) }} tok</span>
                  <span v-if="s.updatedAt">{{ formatTime(s.updatedAt) }}</span>
                </div>
              </button>
            </div>
          </div>
        </div>
      </n-card>

      <!-- 右侧对话区 -->
      <div class="chat-main">
        <div class="chat-toolbar">
          <div class="toolbar-left">
            <span class="toolbar-title" :title="currentSessionTitle">{{ currentSessionTitle }}</span>
            <div v-if="store.defaultModel || sessionUsageLabel" class="toolbar-tags">
              <n-tag v-if="store.defaultModel" size="small" :bordered="false" class="toolbar-tag">
                {{ store.defaultModel }}
              </n-tag>
              <n-tag
                v-if="sessionUsageLabel"
                size="small"
                type="info"
                :bordered="false"
                class="toolbar-tag"
              >
                {{ sessionUsageLabel }}
              </n-tag>
            </div>
          </div>
          <div class="toolbar-actions">
            <n-space :size="8" :wrap="false">
              <n-button size="small" quaternary :loading="store.loadingHistory" @click="refreshChat">
                刷新记录
              </n-button>
              <n-button size="small" quaternary @click="newChat">新对话</n-button>
              <n-button
                v-if="store.isWaitingReply"
                size="small"
                type="warning"
                @click="store.abortRun()"
              >
                停止
              </n-button>
            </n-space>
          </div>
        </div>

        <div class="chat-body">
          <div v-if="store.loadingHistory" class="chat-center">
            <n-spin size="medium" />
            <n-text depth="3" style="margin-top: 8px">加载消息...</n-text>
          </div>
          <div
            v-else-if="store.visibleMessages.length === 0 && !store.streamingMessage && !store.isWaitingReply"
            class="chat-center welcome"
          >
            <n-empty description="开始对话">
              <template #extra>
                <n-text depth="3">在下方输入消息，Enter 发送</n-text>
              </template>
            </n-empty>
          </div>
          <div
            v-else
            ref="messageListRef"
            class="chat-messages"
            @scroll="onMessagesScroll"
          >
            <div class="chat-messages-inner">
              <ChatMessageRow
                v-for="msg in store.visibleMessages"
                :key="msg.id"
                v-memo="[msg.id, msg.role, partsFingerprint(msg.parts), msg.content]"
                :msg="msg"
              />
              <ChatMessageRow
                v-if="store.streamingMessage"
                :key="store.streamingMessage.id"
                :msg="store.streamingMessage"
              />
            </div>
          </div>

          <n-button
            v-if="showScrollFab"
            class="scroll-fab"
            circle
            size="small"
            type="primary"
            secondary
            @click="scrollToBottom(true)"
          >
            ↓
          </n-button>
        </div>

        <div class="chat-footer">
          <ChatComposerAttachments
            ref="attachmentsRef"
            :queue="uploadQueue"
            :disabled="!store.gatewayConnected || store.isWaitingReply"
            @change="onAttachmentsChange"
          >
            <n-input
              v-model:value="inputText"
              type="textarea"
              class="composer-input"
              :placeholder="inputPlaceholder"
              :autosize="{ minRows: 2, maxRows: 6 }"
              :disabled="!store.gatewayConnected || store.isWaitingReply"
              @keydown="onInputKeydown"
              @paste="onPaste"
            />
            <template #actions>
              <n-button
                type="primary"
                class="send-btn"
                :loading="store.sending || sendingAttachments"
                :disabled="!canSend"
                @click="send"
              >
                发送
              </n-button>
            </template>
          </ChatComposerAttachments>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NButton, NCard, NEmpty, NInput, NSkeleton, NSpace, NSpin, NTag, NText,
} from 'naive-ui';
import ChatComposerAttachments from '@/components/chat/ChatComposerAttachments.vue';
import ChatMessageRow from '@/components/chat/ChatMessageRow.vue';
import { useOpenClawChatStore } from '@/stores/openclawChat';
import { partsFingerprint } from '@/utils/chatParts';
import { ChatUploadQueue } from '@/utils/chatUploadQueue';

const message = useMessage();
const store = useOpenClawChatStore();

const inputText = ref('');
const uploadQueue = new ChatUploadQueue();
const attachmentsRef = ref<InstanceType<typeof ChatComposerAttachments> | null>(null);
const attachmentTick = ref(0);
const sendingAttachments = ref(false);
const sessionSearch = ref('');
const messageListRef = ref<HTMLElement | null>(null);
const stickToBottom = ref(true);
const showScrollFab = ref(false);
let scrollRaf = 0;

const filteredSessions = computed(() => {
  const today = new Date().toDateString();
  let list = store.sessions.filter(
    (s) => s.updatedAt && new Date(s.updatedAt).toDateString() === today,
  );

  const q = sessionSearch.value.trim().toLowerCase();
  if (q) {
    list = list.filter(
      (s) =>
        s.title.toLowerCase().includes(q) ||
        s.key.toLowerCase().includes(q) ||
        (s.lastMessagePreview?.toLowerCase().includes(q) ?? false),
    );
  }

  return list.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
});

const emptySessionHint = computed(() => {
  if (sessionSearch.value.trim()) return '没有符合搜索条件的今日会话';
  const today = new Date().toDateString();
  if (store.sessions.some((s) => s.updatedAt && new Date(s.updatedAt).toDateString() !== today)) {
    return '今日暂无会话';
  }
  return '暂无会话';
});

const currentSessionTitle = computed(() => {
  const s = store.sessions.find((x) => x.key === store.sessionKey);
  return s?.title ?? store.sessionKey;
});

const canSend = computed(() => {
  void attachmentTick.value;
  return (
    store.gatewayConnected &&
    !store.sending &&
    !store.isWaitingReply &&
    !uploadQueue.hasUploading &&
    (inputText.value.trim().length > 0 || uploadQueue.pending.length > 0)
  );
});

const inputPlaceholder = computed(() => {
  if (!store.gatewayConnected) return 'Gateway 未连接，无法发送';
  if (store.isWaitingReply) return '等待回复中…';
  return '输入消息，可粘贴或拖拽图片/文档…';
});

const sessionUsageLabel = computed(() => {
  const u = store.sessionUsage;
  if (!u || u.totalTokens <= 0) return '';
  let label = `Token ${formatTokens(u.totalTokens)}`;
  if (u.inputTokens > 0 || u.outputTokens > 0) {
    label += `（输入 ${formatTokens(u.inputTokens)} / 输出 ${formatTokens(u.outputTokens)}）`;
  }
  if (u.totalCost != null && u.totalCost > 0) {
    label += ` · $${u.totalCost.toFixed(4)}`;
  }
  return label;
});

onMounted(() => {
  void store.initView();
});

onUnmounted(() => {
  store.setChatViewActive(false);
  uploadQueue.clear();
});

watch(
  () => store.lastError,
  (err) => {
    if (err) {
      message.error(err, { duration: 5000 });
      store.clearError();
    }
  }
);

watch(
  () => store.visibleMessages.length,
  () => {
    if (stickToBottom.value) scheduleScrollToBottom();
  }
);

watch(
  () => store.streamText,
  () => {
    if (stickToBottom.value) scheduleScrollToBottom(false);
  }
);

watch(
  () => store.sessionKey,
  () => {
    stickToBottom.value = true;
    showScrollFab.value = false;
  }
);

watch(
  () => store.loadingHistory,
  (loading, wasLoading) => {
    if (wasLoading && !loading) {
      stickToBottom.value = true;
      scheduleScrollToBottom();
      requestAnimationFrame(() => scheduleScrollToBottom());
    }
  }
);

function onMessagesScroll() {
  const el = messageListRef.value;
  if (!el) return;
  const gap = el.scrollHeight - el.scrollTop - el.clientHeight;
  stickToBottom.value = gap < 80;
  showScrollFab.value = gap >= 80;
}

function scheduleScrollToBottom(smooth = false) {
  if (scrollRaf) cancelAnimationFrame(scrollRaf);
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    scrollToBottom(smooth);
  });
}

function scrollToBottom(smooth = false) {
  nextTick(() => {
    const el = messageListRef.value;
    if (!el) return;
    if (!smooth) {
      el.scrollTop = el.scrollHeight;
      stickToBottom.value = true;
      showScrollFab.value = false;
      return;
    }
    requestAnimationFrame(() => {
      const inner = el.querySelector<HTMLElement>('.chat-messages-inner');
      const last = inner?.lastElementChild;
      if (last) {
        last.scrollIntoView({ block: 'end', behavior: 'smooth' });
      } else {
        el.scrollTop = el.scrollHeight;
      }
      stickToBottom.value = true;
      showScrollFab.value = false;
    });
  });
}

function onAttachmentsChange() {
  attachmentTick.value += 1;
}

async function onPaste(e: ClipboardEvent) {
  const files = e.clipboardData?.files;
  if (!files?.length) return;
  const hasBinary = Array.from(files).some(
    (f) => f.type.startsWith('image/') || f.type === 'application/pdf' || f.type.startsWith('text/'),
  );
  if (!hasBinary) return;
  e.preventDefault();
  await attachmentsRef.value?.addFromClipboard(files);
  onAttachmentsChange();
}

async function send() {
  const text = inputText.value.trim();
  const hasAttachments = uploadQueue.pending.length > 0;
  if (!text && !hasAttachments) return;

  stickToBottom.value = true;
  sendingAttachments.value = true;
  try {
    const parts = hasAttachments ? await uploadQueue.flush() : [];
    inputText.value = '';
    uploadQueue.clear();
    onAttachmentsChange();
    await store.sendMessage(text, parts);
  } catch (e) {
    message.error(e instanceof Error ? e.message : '附件上传失败');
    onAttachmentsChange();
  } finally {
    sendingAttachments.value = false;
  }
}

function onInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    if (canSend.value) send();
  }
}

async function refreshSessions() {
  await store.fetchSessions();
  message.success('会话列表已更新');
}

async function refreshChat() {
  await store.refreshAll();
  message.success('已刷新');
}

async function newChat() {
  const ok = await store.startNewSession();
  if (ok) {
    message.success('已开始新对话');
  } else {
    message.error(store.lastError || '新建对话失败');
  }
}

function formatTime(ts: number) {
  const d = new Date(ts);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric' });
}

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}
</script>

<style scoped>
.chat-page {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-page :deep(.page-header) {
  flex-shrink: 0;
}

.chat-page :deep(.page-header-left p) {
  color: var(--oc-muted);
}

.chat-page .page-body {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-layout {
  flex: 1 1 0;
  min-height: 0;
  min-width: 0;
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 280px) minmax(0, 1fr);
  gap: 16px;
  overflow: hidden;
}

/* —— 左侧会话栏 —— */
.chat-sidebar {
  min-height: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: var(--oc-shadow-card);
}

.chat-sidebar :deep(.n-card-header) {
  flex-shrink: 0;
}

.chat-sidebar :deep(.n-card-header .n-text) {
  color: var(--oc-chat-msg-text) !important;
  font-size: 14px;
}

.chat-sidebar :deep(.n-input__input-el),
.chat-sidebar :deep(.n-input__textarea-el) {
  color: var(--oc-chat-msg-text) !important;
}

.chat-sidebar :deep(.n-input__placeholder) {
  color: var(--oc-chat-msg-muted) !important;
}

.chat-sidebar :deep(.n-card-content) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-top: 0;
}

.sidebar-tools {
  flex-shrink: 0;
  margin-bottom: 8px;
}

.sidebar-hint {
  flex-shrink: 0;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: var(--oc-radius-md);
  background: var(--oc-chat-msg-system-bg);
  border: 1px solid var(--oc-chat-msg-assistant-border);
}

.sidebar-hint :deep(.n-text) {
  color: var(--oc-chat-msg-system-text) !important;
  line-height: 1.5;
}

.sidebar-hint code {
  font-size: 11px;
  color: var(--oc-primary);
  font-family: var(--n-font-family-mono);
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-loading {
  padding: 8px 0;
}

.session-scroll {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 8px;
}

.session-item {
  width: 100%;
  text-align: left;
  padding: 10px 12px 10px 14px;
  border: 1px solid transparent;
  border-radius: var(--oc-radius-md);
  background: transparent;
  cursor: pointer;
  transition:
    background var(--oc-transition),
    border-color var(--oc-transition),
    box-shadow var(--oc-transition);
}

.session-item:hover {
  background: var(--oc-chat-session-active);
  border-color: var(--oc-chat-msg-assistant-border);
}

.session-item.active {
  background: var(--oc-chat-session-active);
  border-color: var(--oc-primary);
  box-shadow: inset 3px 0 0 var(--oc-primary);
}

.session-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--oc-chat-msg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: var(--oc-primary);
}

.session-preview {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--oc-chat-msg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--oc-chat-msg-muted);
}

.session-meta span:last-child {
  flex-shrink: 0;
  opacity: 0.85;
}

/* —— 右侧对话主面板 —— */
.chat-main {
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--oc-chat-msg-assistant-border);
  border-radius: var(--oc-radius-lg);
  background: var(--oc-chat-panel-bg);
  box-shadow: var(--oc-shadow-card);
  overflow: hidden;
  backdrop-filter: blur(8px);
}

.chat-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--oc-chat-msg-assistant-border);
  background: var(--oc-chat-composer-bg);
}

.toolbar-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  overflow: hidden;
}

.toolbar-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--oc-chat-msg-text);
}

.toolbar-tags {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  flex-shrink: 1;
  min-width: 0;
  max-width: min(42%, 280px);
}

.toolbar-tag {
  flex-shrink: 1;
  min-width: 0;
  max-width: 100%;
}

.toolbar-tag :deep(.n-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 220px;
}

.toolbar-left :deep(.n-tag) {
  font-size: 12px;
  color: var(--oc-chat-msg-muted) !important;
}

.toolbar-actions {
  flex-shrink: 0;
}

.toolbar-actions :deep(.n-space) {
  flex-wrap: nowrap !important;
}

.chat-toolbar :deep(.n-button) {
  color: var(--oc-chat-msg-text);
}

.chat-toolbar :deep(.n-button:not(.n-button--disabled):hover) {
  color: var(--oc-primary);
}

.chat-body {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--oc-chat-thread-bg);
}

.chat-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
}

.chat-center.welcome :deep(.n-empty .n-empty__description) {
  color: var(--oc-chat-msg-text);
  font-size: 15px;
  font-weight: 500;
}

.chat-center :deep(.n-text) {
  color: var(--oc-chat-msg-muted) !important;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-anchor: none;
  overscroll-behavior: contain;
}

.chat-messages-inner {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: stretch;
  gap: 22px;
  padding: 24px 20px;
  min-height: min-content;
}

.chat-messages-inner > :deep(.msg-row) {
  flex-shrink: 0;
}

.scroll-fab {
  position: absolute;
  right: 20px;
  bottom: 20px;
  z-index: 2;
  box-shadow: var(--oc-shadow-card-hover);
}

/* —— 输入区 —— */
.chat-footer {
  flex-shrink: 0;
  display: flex;
  padding: 12px 16px 14px;
  border-top: 1px solid var(--oc-chat-msg-assistant-border);
  background: var(--oc-chat-composer-bg);
}

.send-btn {
  min-width: 72px;
  height: 36px;
  font-weight: 600;
  border-radius: 10px;
  box-shadow: 0 2px 8px var(--oc-glow);
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    max-height: 200px;
  }
}
</style>
