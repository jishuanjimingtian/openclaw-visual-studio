import { describe, expect, it, beforeEach, vi, afterEach } from 'vitest';
import {
  buildPreview,
  loadChatNotifyPrefs,
  maybeNotifyReply,
  notifyQueue,
  notifyTick,
  resetNotifyStateForTests,
  setChatNotifyEnabled,
  setNotifyRoutePath,
  shouldNotifyReply,
} from '@/utils/chatNotify';

describe('chatNotify', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    resetNotifyStateForTests();
    loadChatNotifyPrefs();
    setNotifyRoutePath('/dashboard');
    setChatNotifyEnabled(true);
    vi.stubGlobal('document', {
      ...document,
      hidden: false,
      hasFocus: () => true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function flushNotifyQueue() {
    vi.advanceTimersByTime(500);
  }

  it('buildPreview truncates long text', () => {
    const long = 'a'.repeat(150);
    expect(buildPreview(long).length).toBeLessThanOrEqual(101);
  });

  it('shouldNotifyReply when not on chat page', () => {
    setNotifyRoutePath('/dashboard');
    expect(shouldNotifyReply()).toBe(true);
  });

  it('should not notify on chat page when focused', () => {
    setNotifyRoutePath('/chat');
    expect(shouldNotifyReply()).toBe(false);
  });

  it('should notify on chat page when document hidden', () => {
    setNotifyRoutePath('/chat');
    vi.stubGlobal('document', {
      ...document,
      hidden: true,
      hasFocus: () => true,
    });
    expect(shouldNotifyReply()).toBe(true);
  });

  it('dedupes by runId', () => {
    maybeNotifyReply({
      sessionKey: 'agent:main:main',
      sessionTitle: 'Main',
      text: 'hello',
      runId: 'run-1',
    });
    maybeNotifyReply({
      sessionKey: 'agent:main:main',
      sessionTitle: 'Main',
      text: 'hello again',
      runId: 'run-1',
    });
    flushNotifyQueue();
    expect(notifyQueue.value.length).toBe(1);
    expect(notifyTick.value).toBe(1);
  });

  it('does not consume dedupe when notification suppressed', () => {
    setNotifyRoutePath('/chat');
    maybeNotifyReply({
      sessionKey: 'agent:main:main',
      sessionTitle: 'Main',
      text: 'hello',
      runId: 'run-x',
    });
    flushNotifyQueue();
    expect(notifyQueue.value.length).toBe(0);

    setNotifyRoutePath('/dashboard');
    maybeNotifyReply({
      sessionKey: 'agent:main:main',
      sessionTitle: 'Main',
      text: 'hello',
      runId: 'run-x',
    });
    flushNotifyQueue();
    expect(notifyQueue.value.length).toBe(1);
  });

  it('skips when notify disabled', () => {
    setChatNotifyEnabled(false);
    maybeNotifyReply({
      sessionKey: 'k',
      sessionTitle: 'T',
      text: 'hi',
      runId: 'run-2',
    });
    flushNotifyQueue();
    expect(notifyQueue.value.length).toBe(0);
  });
});
