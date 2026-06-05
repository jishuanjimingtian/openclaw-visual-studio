import { messageContent, normalizeChatParts, partsFingerprint, summarizeTextFromParts } from '@/utils/chatParts';
import type { ChatMessage, ChatPart, UiChatMessage } from '@shared/types';

/** OpenClaw 内部占位回复（如心跳、静默确认），不应展示在聊天 UI */
const HIDDEN_REPLY = /^\s*(?:NO_REPLY|HEARTBEAT_OK)\s*$/i;

export function isHiddenChatContent(content: string | null | undefined): boolean {
  if (!content?.trim()) return true;
  return HIDDEN_REPLY.test(content.trim());
}

export function isHiddenMessage(message: ChatMessage): boolean {
  const parts = normalizeChatParts(message);
  const hasAttachment = parts.some((p) => p.type === 'image' || p.type === 'file');
  if (hasAttachment) return false;
  return isHiddenChatContent(messageContent(message));
}

/** 为缺失时间戳的历史消息生成稳定、单调递增的时间，避免排序打乱 Gateway 原始顺序 */
export function assignHistoryTimestamps(messages: ChatMessage[]): ChatMessage[] {
  if (messages.length === 0) return messages;

  const known = messages.map((m) => m.timestamp).filter((t): t is number => t != null);
  const base =
    known.length > 0
      ? Math.min(...known) - (messages.length - known.length) * 1000
      : Date.now() - messages.length * 1000;

  let seq = 0;
  return messages.map((m) => {
    if (m.timestamp != null) return m;
    const stamped = { ...m, timestamp: base + seq * 1000 };
    seq += 1;
    return stamped;
  });
}

/** 稳定 id：不含 timestamp，避免轮询刷新后 Vue key 变化导致列表跳动/乱序 */
export function stableMessageId(role: string, content: string, index?: number, parts?: ChatPart[]): string {
  const fp = parts?.length ? partsFingerprint(parts) : content.slice(0, 48).replace(/\s+/g, ' ');
  return `${role.toLowerCase()}:${index ?? 0}:${fp.slice(0, 64)}`;
}

export function toUiMessage(
  role: string,
  content: string,
  timestamp?: number,
  index?: number,
  parts?: ChatPart[],
): UiChatMessage {
  const ts = timestamp ?? Date.now();
  const normalizedParts = parts?.length ? parts : content ? [{ type: 'text' as const, text: content }] : [];
  return {
    id: stableMessageId(role.toLowerCase(), content, index, normalizedParts),
    role: role.toLowerCase(),
    content,
    parts: normalizedParts.length ? normalizedParts : undefined,
    timestamp: ts,
  };
}

/** 按原始顺序去重相邻重复项，不再按时间戳重排 */
export function dedupeMessages(list: UiChatMessage[]): UiChatMessage[] {
  const out: UiChatMessage[] = [];
  for (const msg of list) {
    const prev = out[out.length - 1];
    const content = messageContent(msg);
    const prevContent = prev ? messageContent(prev) : '';
    if (
      prev &&
      prev.role === msg.role &&
      prevContent === content &&
      partsFingerprint(prev.parts) === partsFingerprint(msg.parts) &&
      Math.abs((prev.timestamp ?? 0) - (msg.timestamp ?? 0)) < 2000
    ) {
      continue;
    }
    out.push(msg);
  }
  return out;
}

export function mapHistoryToUi(messages: ChatMessage[]): UiChatMessage[] {
  const visible = messages.filter((m) => !isHiddenMessage(m));
  const ordered = assignHistoryTimestamps(visible);
  return dedupeMessages(
    ordered.map((m, index) => {
      const parts = normalizeChatParts(m);
      const content = summarizeTextFromParts(parts) || m.content || '';
      return toUiMessage(m.role, content, m.timestamp, index, parts);
    }),
  );
}

/** 比较消息列表是否一致，避免轮询刷新触发无意义的 Vue 重渲染 */
export function sameMessageList(a: UiChatMessage[], b: UiChatMessage[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const left = a[i];
    const right = b[i];
    if (
      left.id !== right.id ||
      left.role !== right.role ||
      messageContent(left) !== messageContent(right) ||
      partsFingerprint(left.parts) !== partsFingerprint(right.parts) ||
      Boolean(left.streaming) !== Boolean(right.streaming)
    ) {
      return false;
    }
  }
  return true;
}

export function mergeHistoryWithPending(
  remote: UiChatMessage[],
  local: UiChatMessage[],
): UiChatMessage[] {
  const pending = local.filter(
    (m) =>
      m.role === 'user' &&
      !remote.some(
        (r) =>
          r.role === 'user' &&
          messageContent(r) === messageContent(m) &&
          partsFingerprint(r.parts) === partsFingerprint(m.parts),
      ),
  );
  if (pending.length === 0) return dedupeMessages(remote);

  const merged = [...remote];
  for (const p of pending) {
    const ts = p.timestamp ?? Number.MAX_SAFE_INTEGER;
    const insertAt = merged.findIndex((m) => (m.timestamp ?? 0) > ts);
    merged.splice(insertAt === -1 ? merged.length : insertAt, 0, p);
  }
  return dedupeMessages(merged);
}
