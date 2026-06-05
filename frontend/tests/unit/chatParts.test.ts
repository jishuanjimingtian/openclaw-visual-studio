import { describe, expect, it } from 'vitest';
import {
  messageContent,
  normalizeChatParts,
  partsFingerprint,
  summarizeTextFromParts,
} from '@/utils/chatParts';
import type { ChatMessage } from '@shared/types';

describe('chatParts', () => {
  it('normalizes legacy content-only messages', () => {
    const msg: ChatMessage = { role: 'user', content: 'hello' };
    expect(normalizeChatParts(msg)).toEqual([{ type: 'text', text: 'hello' }]);
  });

  it('summarizes text parts only', () => {
    const parts = [
      { type: 'text' as const, text: 'a' },
      { type: 'image' as const, attachmentId: '1', name: 'x', mime: 'image/png', sizeBytes: 1 },
      { type: 'text' as const, text: 'b' },
    ];
    expect(summarizeTextFromParts(parts)).toBe('a\nb');
    expect(messageContent({ role: 'user', parts })).toBe('a\nb');
  });

  it('builds stable fingerprint without blob data', () => {
    const a = partsFingerprint([
      { type: 'image', attachmentId: 'id-1', name: 'a', mime: 'image/png', sizeBytes: 10 },
    ]);
    const b = partsFingerprint([
      { type: 'image', attachmentId: 'id-1', name: 'a', mime: 'image/png', sizeBytes: 99 },
    ]);
    expect(a).toBe(b);
  });
});
