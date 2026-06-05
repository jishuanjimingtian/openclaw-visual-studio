import { describe, expect, it } from 'vitest';
import {
  assignHistoryTimestamps,
  dedupeMessages,
  mapHistoryToUi,
  mergeHistoryWithPending,
  sameMessageList,
} from '@/utils/chatHistory';
import type { UiChatMessage } from '@shared/types';

describe('chatHistory', () => {
  it('preserves gateway order when timestamps are missing', () => {
    const ui = mapHistoryToUi([
      { role: 'user', content: 'first' },
      { role: 'assistant', content: 'second' },
      { role: 'user', content: 'third' },
    ]);
    expect(ui.map((m) => m.content)).toEqual(['first', 'second', 'third']);
  });

  it('assigns monotonic timestamps for missing values', () => {
    const stamped = assignHistoryTimestamps([
      { role: 'user', content: 'a' },
      { role: 'assistant', content: 'b' },
    ]);
    expect(stamped[0].timestamp).toBeDefined();
    expect(stamped[1].timestamp).toBeGreaterThan(stamped[0].timestamp!);
  });

  it('dedupes adjacent duplicates without reordering', () => {
    const list: UiChatMessage[] = [
      { id: '1', role: 'user', content: 'hi', timestamp: 100 },
      { id: '2', role: 'user', content: 'hi', timestamp: 101 },
      { id: '3', role: 'assistant', content: 'ok', timestamp: 200 },
    ];
    expect(dedupeMessages(list).map((m) => m.id)).toEqual(['1', '3']);
  });

  it('appends pending local user messages after remote history', () => {
    const remote: UiChatMessage[] = [
      { id: '1', role: 'user', content: 'old', timestamp: 1 },
      { id: '2', role: 'assistant', content: 'reply', timestamp: 2 },
    ];
    const local: UiChatMessage[] = [
      { id: '3', role: 'user', content: 'new', timestamp: 3 },
    ];
    const merged = mergeHistoryWithPending(remote, local);
    expect(merged.map((m) => m.content)).toEqual(['old', 'reply', 'new']);
  });

  it('keeps stable message id across timestamp changes', () => {
    const a = mapHistoryToUi([{ role: 'user', content: 'hi', timestamp: 100 }]);
    const b = mapHistoryToUi([{ role: 'user', content: 'hi', timestamp: 999_999 }]);
    expect(a[0].id).toBe(b[0].id);
  });

  it('inserts pending user by timestamp when remote lacks it', () => {
    const remote: UiChatMessage[] = [
      { id: '1', role: 'user', content: 'a', timestamp: 10 },
      { id: '2', role: 'assistant', content: 'b', timestamp: 30 },
    ];
    const local: UiChatMessage[] = [
      { id: '3', role: 'user', content: 'between', timestamp: 20 },
    ];
    const merged = mergeHistoryWithPending(remote, local);
    expect(merged.map((m) => m.content)).toEqual(['a', 'between', 'b']);
  });

  it('detects identical message lists for skip-render optimization', () => {
    const a: UiChatMessage[] = [
      { id: '1', role: 'user', content: 'hi', timestamp: 1 },
      { id: '2', role: 'assistant', content: 'ok', timestamp: 2 },
    ];
    const b: UiChatMessage[] = [
      { id: '1', role: 'user', content: 'hi', timestamp: 99 },
      { id: '2', role: 'assistant', content: 'ok', timestamp: 100 },
    ];
    expect(sameMessageList(a, b)).toBe(true);
    expect(sameMessageList(a, [{ ...b[0], content: 'changed' }, b[1]])).toBe(false);
  });
});
