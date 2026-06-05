import type { AttachmentPart, ChatMessage, ChatPart, TextPart, UiChatMessage } from '@shared/types';

export function isTextPart(part: ChatPart): part is TextPart {
  return part.type === 'text';
}

export function isAttachmentPart(part: ChatPart): part is AttachmentPart {
  return part.type === 'image' || part.type === 'file';
}

export function normalizeChatParts(message: ChatMessage): ChatPart[] {
  if (message.parts && message.parts.length > 0) {
    return message.parts;
  }
  const text = message.content?.trim() ?? '';
  if (!text) return [];
  return [{ type: 'text', text: message.content ?? '' }];
}

export function summarizeTextFromParts(parts: ChatPart[]): string {
  return parts
    .filter(isTextPart)
    .map((p) => p.text)
    .join('\n')
    .trim();
}

export function messageContent(message: ChatMessage | UiChatMessage): string {
  const fromParts = summarizeTextFromParts(normalizeChatParts(message));
  if (fromParts) return fromParts;
  return message.content?.trim() ?? '';
}

export function partsFingerprint(parts: ChatPart[] | undefined): string {
  if (!parts?.length) return '';
  return parts
    .map((p) => {
      if (isTextPart(p)) return `t:${p.text.slice(0, 32)}`;
      return `a:${p.type}:${p.attachmentId}:${p.thumbReady ? 1 : 0}`;
    })
    .join('|');
}

export function attachmentPartFromRef(
  ref: { attachmentId: string; name: string; mime: string; sizeBytes: number; width?: number; height?: number; thumbReady?: boolean },
  type: 'image' | 'file',
): AttachmentPart {
  return {
    type,
    attachmentId: ref.attachmentId,
    name: ref.name,
    mime: ref.mime,
    sizeBytes: ref.sizeBytes,
    width: ref.width,
    height: ref.height,
    thumbReady: ref.thumbReady ?? false,
  };
}

export function inferAttachmentType(mime: string): 'image' | 'file' {
  return mime.toLowerCase().startsWith('image/') ? 'image' : 'file';
}
