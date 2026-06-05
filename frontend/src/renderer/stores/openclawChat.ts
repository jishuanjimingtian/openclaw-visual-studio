import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { conversationApi } from '@/api/conversation';
import { openclawChatApi } from '@/api/openclawChat';
import {
  dedupeMessages,
  isHiddenChatContent,
  isHiddenMessage,
  mapHistoryToUi,
  mergeHistoryWithPending,
  sameMessageList,
  toUiMessage,
} from '@/utils/chatHistory';
import { messageContent } from '@/utils/chatParts';
import type {
  ChatPart,
  OpenClawChatEvent,
  OpenClawSession,
  OpenClawSessionUsage,
  UiChatMessage,
} from '@shared/types';
import {
  loadChatNotifyPrefs,
  maybeNotifyReply,
  resolveSessionTitle,
} from '@/utils/chatNotify';

const DEFAULT_SESSION_KEY = 'agent:main:main';
const SESSION_STORAGE_KEY = 'openclaw-chat-session';
const STREAM_ERROR_FALLBACK = /^\[assistant turn failed before producing content\]$/i;

function humanizeAssistantContent(content: string): string {
  const trimmed = content.trim();
  if (STREAM_ERROR_FALLBACK.test(trimmed)) {
    return '模型未返回有效内容。请检查模型/API 配置，或运行 openclaw logs --follow 查看 Gateway 日志。';
  }
  return content;
}
const STATUS_POLL_MS = 20_000;
const REPLY_POLL_INITIAL_MS = 600;
const REPLY_POLL_MAX_MS = 3_500;
const REPLY_POLL_BACKOFF = 1.35;
/** 等待回复最长约 3 分钟（指数退避轮询） */
const REPLY_POLL_MAX_ATTEMPTS = 55;
const POLL_HISTORY_LIMIT = 24;
const POLL_HISTORY_MAX_CHARS = 4_000;

const SSE_RECONNECT_INITIAL_MS = 1_000;
const SSE_RECONNECT_MAX_MS = 15_000;
const REFRESH_SESSIONS_DEBOUNCE_MS = 3_000;

function newId() {
  return crypto.randomUUID();
}

export const useOpenClawChatStore = defineStore('openclawChat', () => {
  const gatewayConnected = ref(false);
  const sseConnected = ref(false);
  const defaultModel = ref<string | null>(null);
  const sessions = ref<OpenClawSession[]>([]);
  const loadingSessions = ref(false);

  const sessionKey = ref(DEFAULT_SESSION_KEY);
  const messages = ref<UiChatMessage[]>([]);
  const streamText = ref('');
  const chatRunId = ref<string | null>(null);
  const sending = ref(false);
  const loadingHistory = ref(false);
  const lastError = ref<string | null>(null);
  const sessionUsage = ref<OpenClawSessionUsage | null>(null);
  const chatViewActive = ref(false);

  let eventSource: EventSource | null = null;
  let statusPollTimer: ReturnType<typeof setInterval> | null = null;
  let replyPollTimer: ReturnType<typeof setTimeout> | null = null;
  let refreshSessionsTimer: ReturnType<typeof setTimeout> | null = null;
  let replyPollCount = 0;
  let replyPollDelayMs = REPLY_POLL_INITIAL_MS;
  let sseChatSeenForRun = false;
  let streamFlushRaf = 0;
  let pendingStreamText: string | null = null;
  let sseReconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let sseReconnectAttempts = 0;
  let destroyed = false;
  let globalInitialized = false;

  const isWaitingReply = computed(() => chatRunId.value !== null);

  const visibleMessages = computed(() =>
    messages.value.filter((m) => !isHiddenMessage(m))
  );

  /** 流式回复单独渲染，避免每次 token 更新触发整表 v-for 重绘 */
  const streamingMessage = computed((): UiChatMessage | null => {
    if (!chatRunId.value) return null;

    const streamContent = streamText.value.trim();
    if (streamContent && !isHiddenChatContent(streamContent)) {
      return {
        id: `stream-${chatRunId.value}`,
        role: 'assistant',
        content: streamContent,
        timestamp: 0,
        streaming: true,
      };
    }
    if (!sending.value) {
      return {
        id: `thinking-${chatRunId.value}`,
        role: 'assistant',
        content: '',
        timestamp: 0,
        streaming: true,
      };
    }
    return null;
  });

  /** @deprecated 使用 visibleMessages + streamingMessage */
  const displayMessages = computed(() => {
    const list = [...visibleMessages.value];
    const streaming = streamingMessage.value;
    if (streaming) list.push(streaming);
    return list;
  });

  function clearReplyPoll() {
    if (replyPollTimer) {
      clearTimeout(replyPollTimer);
      replyPollTimer = null;
    }
    replyPollCount = 0;
    replyPollDelayMs = REPLY_POLL_INITIAL_MS;
    sseChatSeenForRun = false;
  }

  function clearStream() {
    pendingStreamText = null;
    if (streamFlushRaf) {
      cancelAnimationFrame(streamFlushRaf);
      streamFlushRaf = 0;
    }
    streamText.value = '';
    chatRunId.value = null;
    clearReplyPoll();
  }

  function flushStreamText() {
    streamFlushRaf = 0;
    if (pendingStreamText == null) return;
    const next = pendingStreamText;
    pendingStreamText = null;
    if (!next || isHiddenChatContent(next)) return;
    if (!streamText.value || next.length >= streamText.value.length) {
      streamText.value = next;
    }
  }

  function commitStreamText() {
    if (streamFlushRaf) {
      cancelAnimationFrame(streamFlushRaf);
      streamFlushRaf = 0;
    }
    flushStreamText();
  }

  function scheduleStreamText(next: string) {
    pendingStreamText = next;
    if (streamFlushRaf) return;
    streamFlushRaf = requestAnimationFrame(flushStreamText);
  }

  function applyMessages(next: UiChatMessage[]) {
    if (!sameMessageList(messages.value, next)) {
      messages.value = next;
    }
  }

  function clearError() {
    lastError.value = null;
  }

  async function fetchSessionUsage() {
    if (!sessionKey.value?.trim() || !gatewayConnected.value) {
      sessionUsage.value = null;
      return;
    }
    try {
      const res = await openclawChatApi.getSessionUsage(sessionKey.value);
      sessionUsage.value = res.data;
    } catch {
      // 保留上次读数，避免闪烁
    }
  }

  async function fetchStatus() {
    try {
      const res = await openclawChatApi.getStatus();
      gatewayConnected.value = res.data.gatewayConnected;
    } catch {
      gatewayConnected.value = false;
    }
  }

  async function fetchSessions() {
    loadingSessions.value = true;
    try {
      const res = await conversationApi.listOpenClawSessions(50);
      gatewayConnected.value = res.data.gatewayConnected;
      defaultModel.value = res.data.defaultModel ?? null;
      sessions.value = res.data.sessions;
      if (sessions.value.length > 0) {
        const exists = sessions.value.some((s) => s.key === sessionKey.value);
        if (!exists && sessionKey.value === DEFAULT_SESSION_KEY) {
          selectSession(sessions.value[0], false);
        }
      }
    } catch {
      gatewayConnected.value = false;
    } finally {
      loadingSessions.value = false;
    }
  }

  /** 从 Gateway 拉取历史；merge=true 时保留尚未入库的本地用户消息 */
  async function pullHistory(
    merge = false,
    light = false,
  ): Promise<{ done: boolean; lastAssistant?: UiChatMessage }> {
    if (!sessionKey.value || !gatewayConnected.value) return { done: false };
    if (!chatViewActive.value && !chatRunId.value) return { done: false };

    try {
      const limit = light ? POLL_HISTORY_LIMIT : 80;
      const maxChars = light ? POLL_HISTORY_MAX_CHARS : 12_000;
      const res = await openclawChatApi.getHistory(sessionKey.value, limit, maxChars, light);
      const remote = mapHistoryToUi(res.data);

      const merged = merge ? mergeHistoryWithPending(remote, messages.value) : remote;

      const prevCount = messages.value.length;
      if (chatViewActive.value) {
        applyMessages(merged);
      }

      const lastAssistant = [...merged].reverse().find((m) => m.role === 'assistant');
      const hasAssistant = remote.some((m) => m.role === 'assistant');
      const grew = merged.length > prevCount;
      return {
        done: hasAssistant && (grew || !chatRunId.value),
        lastAssistant,
      };
    } catch {
      return { done: false };
    }
  }

  async function loadHistory() {
    if (!sessionKey.value || !gatewayConnected.value) {
      if (!mergeMode()) messages.value = [];
      return;
    }
    loadingHistory.value = true;
    try {
      await pullHistory(false);
      lastError.value = null;
      await fetchSessionUsage();
    } catch (e) {
      lastError.value = e instanceof Error ? e.message : '加载历史失败';
    } finally {
      loadingHistory.value = false;
    }
  }

  function mergeMode() {
    return Boolean(chatRunId.value);
  }

  function scheduleReplyPollTick(runId: string) {
    if (replyPollTimer) {
      clearTimeout(replyPollTimer);
    }
    replyPollTimer = setTimeout(() => void replyPollTick(runId), replyPollDelayMs);
  }

  async function replyPollTick(runId: string) {
    if (chatRunId.value !== runId) {
      clearReplyPoll();
      return;
    }

    replyPollCount += 1;

    // SSE 已收到 delta 时降低 chat.history 轮询频率
    const streamActive = streamText.value.length > 0;
    const shouldPoll =
      (!sseChatSeenForRun && !streamActive) || replyPollCount % (streamActive ? 6 : 4) === 0;
    if (shouldPoll) {
      const { done, lastAssistant } = await pullHistory(true, true);
      const last = lastAssistant ?? messages.value[messages.value.length - 1];
      if (last?.role === 'assistant' && messageContent(last).trim()) {
        emitReplyNotifyFromPoll(runId, last.content, false);
        clearStream();
        clearReplyPoll();
        refreshSessionsQuiet();
        if (chatViewActive.value) void fetchSessionUsage();
        return;
      }
      if (done && replyPollCount > 2 && sseChatSeenForRun) {
        clearStream();
        clearReplyPoll();
        if (chatViewActive.value) void fetchSessionUsage();
        return;
      }
    }

    if (replyPollCount >= REPLY_POLL_MAX_ATTEMPTS) {
      lastError.value =
        '等待回复超时。若 Gateway 日志为 LLM request timed out，请提高 openclaw.json 中的 timeoutSeconds 或检查网络/API';
      clearStream();
      clearReplyPoll();
      return;
    }

    replyPollDelayMs = Math.min(
      Math.round(replyPollDelayMs * REPLY_POLL_BACKOFF),
      REPLY_POLL_MAX_MS
    );
    scheduleReplyPollTick(runId);
  }

  function startReplyPoll(runId: string) {
    clearReplyPoll();
    scheduleReplyPollTick(runId);
  }

  function startStatusPoll() {
    stopStatusPoll();
    statusPollTimer = setInterval(() => {
      if (!gatewayConnected.value) {
        fetchStatus().then(() => {
          if (gatewayConnected.value && chatViewActive.value) fetchSessions();
        });
      }
    }, STATUS_POLL_MS);
  }

  function stopStatusPoll() {
    if (statusPollTimer) {
      clearInterval(statusPollTimer);
      statusPollTimer = null;
    }
  }

  function scheduleSseReconnect() {
    if (destroyed || sseReconnectTimer) return;
    const delay = Math.min(
      SSE_RECONNECT_INITIAL_MS * 2 ** sseReconnectAttempts,
      SSE_RECONNECT_MAX_MS
    );
    sseReconnectAttempts += 1;
    sseReconnectTimer = setTimeout(() => {
      sseReconnectTimer = null;
      if (!destroyed) connectEvents();
    }, delay);
  }

  function clearSseReconnect() {
    if (sseReconnectTimer) {
      clearTimeout(sseReconnectTimer);
      sseReconnectTimer = null;
    }
    sseReconnectAttempts = 0;
  }

  function connectEvents() {
    disconnectEvents();
    clearSseReconnect();
    eventSource = new EventSource(openclawChatApi.eventsUrl());

    eventSource.addEventListener('ready', () => {
      sseConnected.value = true;
      clearSseReconnect();
      fetchStatus();
    });

    eventSource.addEventListener('chat', (ev) => {
      try {
        const payload = JSON.parse((ev as MessageEvent).data) as OpenClawChatEvent;
        handleChatEvent(payload);
      } catch {
        // ignore
      }
    });

    eventSource.onerror = () => {
      sseConnected.value = false;
      if (eventSource) {
        eventSource.close();
        eventSource = null;
      }
      scheduleSseReconnect();
    };
  }

  function disconnectEvents() {
    if (eventSource) {
      eventSource.onerror = null;
      eventSource.close();
      eventSource = null;
    }
    sseConnected.value = false;
  }

  function appendAssistant(text: string) {
    const trimmed = humanizeAssistantContent(text.trim());
    if (isHiddenChatContent(trimmed)) return;
    const last = messages.value[messages.value.length - 1];
    if (last?.role === 'assistant' && last.content === trimmed) return;
    messages.value.push({
      ...toUiMessage('assistant', trimmed),
      id: newId(),
    });
  }

  function emitReplyNotify(
    payload: OpenClawChatEvent,
    eventSession: string,
    text: string,
    isError: boolean,
  ) {
    const runId =
      payload.runId?.trim() ||
      (eventSession === sessionKey.value ? chatRunId.value : null) ||
      newId();
    maybeNotifyReply({
      sessionKey: eventSession,
      sessionTitle: resolveSessionTitle(eventSession, sessions.value),
      text,
      runId,
      isError,
    });
  }

  function emitReplyNotifyFromPoll(runId: string, text: string, isError: boolean) {
    maybeNotifyReply({
      sessionKey: sessionKey.value,
      sessionTitle: resolveSessionTitle(sessionKey.value, sessions.value),
      text,
      runId,
      isError,
    });
  }

  function handleChatEvent(payload: OpenClawChatEvent) {
    const eventSession = payload.sessionKey?.trim() || sessionKey.value;
    const isCurrentSession =
      !payload.sessionKey || payload.sessionKey === sessionKey.value;

    if (payload.state === 'delta') {
      if (!isCurrentSession) return;

      const sameRun =
        !payload.runId || !chatRunId.value || payload.runId === chatRunId.value;
      if (!sameRun) return;

      const cumulative = payload.text?.trim();
      const delta = payload.deltaText?.trim();
      if (cumulative && !isHiddenChatContent(cumulative)) {
        if (!streamText.value || cumulative.length >= streamText.value.length) {
          scheduleStreamText(cumulative);
        }
      } else if (delta && !isHiddenChatContent(delta)) {
        scheduleStreamText(streamText.value + delta);
      }
      return;
    }

    if (isCurrentSession) {
      const sameRun =
        !payload.runId || !chatRunId.value || payload.runId === chatRunId.value;
      if (!sameRun && payload.state !== 'final' && payload.state !== 'error') {
        return;
      }

      if (payload.state === 'final' || payload.state === 'error') {
        sseChatSeenForRun = true;
      }

      if (payload.state === 'final') {
        commitStreamText();
        const text = payload.text?.trim();
        let notifyText = '';
        if (text && !isHiddenChatContent(text)) {
          appendAssistant(text);
          notifyText = humanizeAssistantContent(text);
        } else if (streamText.value.trim()) {
          appendAssistant(streamText.value);
          notifyText = humanizeAssistantContent(streamText.value);
        } else {
          void pullHistory(true, true);
          if (text && !isHiddenChatContent(text)) {
            notifyText = humanizeAssistantContent(text);
          }
        }
        if (notifyText) {
          emitReplyNotify(payload, eventSession, notifyText, false);
        }
        clearStream();
        refreshSessionsQuiet();
        if (chatViewActive.value) void fetchSessionUsage();
        return;
      }

      if (payload.state === 'aborted') {
        commitStreamText();
        const text = payload.text?.trim() || streamText.value.trim();
        if (text) appendAssistant(text);
        clearStream();
        return;
      }

      if (payload.state === 'error') {
        commitStreamText();
        const err = payload.errorMessage || payload.text || '对话出错';
        appendAssistant(`⚠️ ${err}`);
        emitReplyNotify(payload, eventSession, err, true);
        clearStream();
        void pullHistory(true, true);
        if (chatViewActive.value) void fetchSessionUsage();
        return;
      }
    }

    if (payload.state === 'final') {
      const text = payload.text?.trim();
      if (text && !isHiddenChatContent(text)) {
        emitReplyNotify(payload, eventSession, humanizeAssistantContent(text), false);
      }
      refreshSessionsQuiet();
      return;
    }

    if (payload.state === 'error') {
      const err = payload.errorMessage || payload.text || '对话出错';
      emitReplyNotify(payload, eventSession, err, true);
      refreshSessionsQuiet();
    }
  }

  async function sendMessage(text: string, extraParts?: ChatPart[]) {
    const msg = text.trim();
    const attachmentParts = (extraParts ?? []).filter(
      (p) => p.type === 'image' || p.type === 'file',
    );
    if ((!msg && attachmentParts.length === 0) || sending.value || isWaitingReply.value || !gatewayConnected.value) {
      return;
    }

    clearError();
    const runId = newId();
    const parts: ChatPart[] = [];
    if (msg) parts.push({ type: 'text', text: msg });
    parts.push(...attachmentParts);

    messages.value = dedupeMessages([
      ...messages.value,
      { ...toUiMessage('user', msg, undefined, undefined, parts), id: newId() },
    ]);
    chatRunId.value = runId;
    streamText.value = '';
    sending.value = true;

    try {
      await openclawChatApi.send(
        sessionKey.value,
        msg,
        runId,
        attachmentParts.length ? attachmentParts : undefined,
      );
      startReplyPoll(runId);
    } catch (e) {
      const err = e instanceof Error ? e.message : '发送失败';
      lastError.value = err;
      appendAssistant(`⚠️ ${err}`);
      clearStream();
    } finally {
      sending.value = false;
    }
  }

  async function abortRun() {
    if (!chatRunId.value) return;
    try {
      await openclawChatApi.abort(sessionKey.value, chatRunId.value);
      await pullHistory(true);
      clearStream();
    } catch (e) {
      lastError.value = e instanceof Error ? e.message : '中止失败';
      clearStream();
    }
  }

  async function startNewSession(): Promise<boolean> {
    if (!gatewayConnected.value) {
      lastError.value = 'Gateway 未连接，请先启动 openclaw gateway';
      return false;
    }
    const parentKey = sessionKey.value?.trim();
    if (!parentKey) {
      lastError.value = '未选择会话';
      return false;
    }
    try {
      if (chatRunId.value) {
        await openclawChatApi.abort(parentKey, chatRunId.value).catch(() => {});
        clearStream();
      }
      const res = await openclawChatApi.resetSession(parentKey);
      const newKey = res.data.sessionKey?.trim();
      if (!newKey) {
        lastError.value = 'Gateway 未返回新会话';
        return false;
      }
      sessionKey.value = newKey;
      sessionStorage.setItem(SESSION_STORAGE_KEY, newKey);
      messages.value = [];
      clearStream();
      clearError();
      await fetchSessions();
      await loadHistory();
      await fetchSessionUsage();
      return true;
    } catch (e) {
      lastError.value = e instanceof Error ? e.message : '新建会话失败';
      return false;
    }
  }

  function selectSession(session: OpenClawSession, reload = true) {
    if (session.key === sessionKey.value && reload) return;
    sessionKey.value = session.key;
    sessionStorage.setItem(SESSION_STORAGE_KEY, session.key);
    clearStream();
    clearError();
    sessionUsage.value = null;
    if (reload) loadHistory();
    else void fetchSessionUsage();
  }

  async function refreshSessionsQuietNow() {
    try {
      const res = await conversationApi.listOpenClawSessions(50);
      gatewayConnected.value = res.data.gatewayConnected;
      defaultModel.value = res.data.defaultModel ?? null;
      sessions.value = res.data.sessions;
    } catch {
      // ignore
    }
  }

  function scheduleRefreshSessionsDebounced() {
    if (refreshSessionsTimer) return;
    refreshSessionsTimer = setTimeout(() => {
      refreshSessionsTimer = null;
      void refreshSessionsQuietNow();
    }, REFRESH_SESSIONS_DEBOUNCE_MS);
  }

  function refreshSessionsQuiet() {
    if (chatViewActive.value) {
      void refreshSessionsQuietNow();
      return;
    }
    scheduleRefreshSessionsDebounced();
  }

  async function refreshAll() {
    clearStream();
    await fetchStatus();
    await fetchSessions();
    await loadHistory();
    await fetchSessionUsage();
  }

  function selectSessionByKey(key: string) {
    const session = sessions.value.find((s) => s.key === key);
    if (session) {
      selectSession(session);
      return;
    }
    sessionKey.value = key;
    sessionStorage.setItem(SESSION_STORAGE_KEY, key);
    clearStream();
    clearError();
    sessionUsage.value = null;
    if (chatViewActive.value) void loadHistory();
  }

  function setChatViewActive(active: boolean) {
    chatViewActive.value = active;
    if (!active && !chatRunId.value) {
      clearReplyPoll();
    }
  }

  async function initGlobal() {
    if (globalInitialized) return;
    globalInitialized = true;
    destroyed = false;
    loadChatNotifyPrefs();

    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (saved) sessionKey.value = saved;

    connectEvents();
    startStatusPoll();
    await fetchStatus();
  }

  async function initView() {
    chatViewActive.value = true;
    await fetchSessions();
    await loadHistory();
    await fetchSessionUsage();
  }

  /** @deprecated 使用 initGlobal + initView */
  async function init() {
    await initGlobal();
    await initView();
  }

  function destroyGlobal() {
    destroyed = true;
    globalInitialized = false;
    chatViewActive.value = false;
    if (refreshSessionsTimer) {
      clearTimeout(refreshSessionsTimer);
      refreshSessionsTimer = null;
    }
    clearSseReconnect();
    disconnectEvents();
    stopStatusPoll();
    clearReplyPoll();
    clearStream();
  }

  /** @deprecated 使用 destroyGlobal */
  function destroy() {
    destroyGlobal();
  }

  return {
    gatewayConnected,
    sseConnected,
    defaultModel,
    sessions,
    loadingSessions,
    sessionKey,
    messages,
    visibleMessages,
    streamingMessage,
    displayMessages,
    streamText,
    chatRunId,
    sending,
    loadingHistory,
    lastError,
    sessionUsage,
    chatViewActive,
    isWaitingReply,
    fetchStatus,
    fetchSessions,
    loadHistory,
    pullHistory,
    connectEvents,
    disconnectEvents,
    sendMessage,
    abortRun,
    startNewSession,
    selectSession,
    selectSessionByKey,
    setChatViewActive,
    clearError,
    refreshAll,
    fetchSessionUsage,
    initGlobal,
    initView,
    destroyGlobal,
    init,
    destroy,
  };
});
