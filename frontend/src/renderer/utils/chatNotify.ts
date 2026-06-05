import { shallowRef, ref } from 'vue';
import { isHiddenChatContent } from '@/utils/chatHistory';

const STORAGE_ENABLED = 'chat-notify-enabled';
const STORAGE_DESKTOP = 'chat-notify-desktop';
const MAX_DEDUPE = 200;
const PREVIEW_MAX = 100;

export interface ChatReplyNotifyItem {
  id: string;
  sessionKey: string;
  sessionTitle: string;
  preview: string;
  isError: boolean;
  runId: string;
}

export interface ChatNotifyPrefs {
  enabled: boolean;
  desktop: boolean;
}

const prefs: ChatNotifyPrefs = {
  enabled: true,
  desktop: true,
};

let currentRoutePath = '/';
const notifiedKeys = new Set<string>();
const notifiedKeyOrder: string[] = [];

/** push 式队列，ChatNotifyHost 监听 notifyTick */
export const notifyQueue = shallowRef<ChatReplyNotifyItem[]>([]);
export const notifyTick = ref(0);

let pendingBatch: ChatReplyNotifyItem[] = [];
let batchTimer: ReturnType<typeof setTimeout> | null = null;
const BATCH_MS = 300;
const MAX_VISIBLE = 3;

function readBool(key: string, defaultValue: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    if (raw === null) return defaultValue;
    return raw === 'true';
  } catch {
    return defaultValue;
  }
}

function writeBool(key: string, value: boolean) {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // ignore
  }
}

export function loadChatNotifyPrefs(): ChatNotifyPrefs {
  prefs.enabled = readBool(STORAGE_ENABLED, true);
  prefs.desktop = readBool(STORAGE_DESKTOP, true);
  return { ...prefs };
}

export function getChatNotifyPrefs(): ChatNotifyPrefs {
  return { ...prefs };
}

export function setChatNotifyEnabled(enabled: boolean) {
  prefs.enabled = enabled;
  writeBool(STORAGE_ENABLED, enabled);
}

export function setChatNotifyDesktop(desktop: boolean) {
  prefs.desktop = desktop;
  writeBool(STORAGE_DESKTOP, desktop);
}

export function setNotifyRoutePath(path: string) {
  currentRoutePath = path;
}

export function shouldNotifyReply(): boolean {
  if (!prefs.enabled) return false;
  return (
    currentRoutePath !== '/chat' ||
    document.hidden ||
    !document.hasFocus()
  );
}

export function buildPreview(text: string): string {
  const oneLine = text.replace(/\s+/g, ' ').trim();
  if (oneLine.length <= PREVIEW_MAX) return oneLine;
  return `${oneLine.slice(0, PREVIEW_MAX)}…`;
}

function dedupeKey(runId: string, sessionKey: string, preview: string): string {
  if (runId) return runId;
  return `${sessionKey}:${preview.slice(0, 48)}`;
}

function rememberNotified(key: string): boolean {
  if (!key) return true;
  if (notifiedKeys.has(key)) return false;
  notifiedKeys.add(key);
  notifiedKeyOrder.push(key);
  while (notifiedKeyOrder.length > MAX_DEDUPE) {
    const old = notifiedKeyOrder.shift();
    if (old) notifiedKeys.delete(old);
  }
  return true;
}

function flushBatchNow() {
  if (batchTimer) {
    clearTimeout(batchTimer);
    batchTimer = null;
  }
  if (pendingBatch.length === 0) return;

  const items = pendingBatch;
  pendingBatch = [];

  if (items.length === 1) {
    notifyQueue.value = [...notifyQueue.value, items[0]];
  } else {
    const merged: ChatReplyNotifyItem = {
      id: crypto.randomUUID(),
      sessionKey: items[items.length - 1].sessionKey,
      sessionTitle: items[items.length - 1].sessionTitle,
      preview: `共 ${items.length} 条新回复`,
      isError: items.some((i) => i.isError),
      runId: items.map((i) => i.runId).join(','),
    };
    notifyQueue.value = [...notifyQueue.value, merged];
  }
  notifyTick.value += 1;
}

function enqueueNotify(item: ChatReplyNotifyItem) {
  pendingBatch.push(item);
  if (pendingBatch.length >= MAX_VISIBLE) {
    flushBatchNow();
    return;
  }
  if (batchTimer) clearTimeout(batchTimer);
  batchTimer = setTimeout(flushBatchNow, BATCH_MS);
}

export function resolveSessionTitle(
  sessionKey: string,
  sessions: { key: string; title: string }[],
): string {
  const found = sessions.find((s) => s.key === sessionKey);
  if (found?.title) return found.title;
  const parts = sessionKey.split(':');
  return parts[parts.length - 1] || sessionKey;
}

export function maybeNotifyReply(params: {
  sessionKey: string;
  sessionTitle: string;
  text: string;
  runId: string;
  isError?: boolean;
}) {
  if (!prefs.enabled) return;
  if (!shouldNotifyReply()) return;

  const trimmed = params.text.trim();
  if (!trimmed || isHiddenChatContent(trimmed)) return;

  const preview = buildPreview(trimmed);
  const key = dedupeKey(params.runId, params.sessionKey, preview);
  if (!rememberNotified(key)) return;

  enqueueNotify({
    id: crypto.randomUUID(),
    sessionKey: params.sessionKey,
    sessionTitle: params.sessionTitle,
    preview,
    isError: params.isError ?? false,
    runId: params.runId || key,
  });
}

function canUseWebNotification(): boolean {
  return typeof Notification !== 'undefined' && Notification.permission === 'granted';
}

export function showDesktopNotification(title: string, body: string, onClick?: () => void) {
  if (!prefs.desktop || !prefs.enabled) return;

  if (typeof window !== 'undefined' && window.electronAPI?.showDesktopNotification) {
    void window.electronAPI.showDesktopNotification({ title, body });
    return;
  }

  if (!canUseWebNotification()) return;

  try {
    const n = new Notification(title, {
      body,
      icon: '/icon.png',
      silent: false,
    });
    if (onClick) {
      n.onclick = () => {
        onClick();
        n.close();
        window.focus();
      };
    }
  } catch {
    // ignore
  }
}

export async function requestDesktopNotifyPermission(): Promise<NotificationPermission | 'unsupported'> {
  if (typeof window !== 'undefined' && window.electronAPI?.showDesktopNotification) {
    return 'granted';
  }
  if (typeof Notification === 'undefined') return 'unsupported';
  if (Notification.permission === 'granted') return 'granted';
  if (Notification.permission === 'denied') return 'denied';
  try {
    return await Notification.requestPermission();
  } catch {
    return 'denied';
  }
}

/** 测试用：重置通知内部状态 */
export function resetNotifyStateForTests() {
  notifyQueue.value = [];
  notifyTick.value = 0;
  pendingBatch = [];
  if (batchTimer) {
    clearTimeout(batchTimer);
    batchTimer = null;
  }
  notifiedKeys.clear();
  notifiedKeyOrder.length = 0;
}

export function consumeNotifyQueue(): ChatReplyNotifyItem[] {
  const items = notifyQueue.value;
  notifyQueue.value = [];
  notifyTick.value = 0;
  return items;
}
