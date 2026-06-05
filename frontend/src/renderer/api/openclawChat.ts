import apiClient from './client';
import { getApiBaseUrl } from '@shared/backend';
import type {
  ChatMessage,
  ChatPart,
  ChatSendResult,
  OpenClawChatStatus,
  OpenClawSessionUsage,
} from '@shared/types';

const BASE_URL = getApiBaseUrl();

export const openclawChatApi = {
  getStatus() {
    return apiClient.get<OpenClawChatStatus>('/openclaw/chat/status');
  },

  getHistory(sessionKey: string, limit = 80, maxChars = 12_000, light = false) {
    return apiClient.get<ChatMessage[]>('/openclaw/chat/history', {
      params: { sessionKey, limit, maxChars, light },
    });
  },

  send(sessionKey: string, message: string, runId?: string, parts?: ChatPart[]) {
    return apiClient.post<ChatSendResult>('/openclaw/chat/send', {
      sessionKey,
      message: message || undefined,
      parts: parts?.length ? parts : undefined,
      runId,
    });
  },

  abort(sessionKey: string, runId?: string) {
    return apiClient.post<{ aborted: boolean }>('/openclaw/chat/abort', {
      sessionKey,
      runId,
    });
  },

  resetSession(sessionKey: string) {
    return apiClient.post<{ sessionKey: string }>('/openclaw/chat/reset', { sessionKey });
  },

  getSessionUsage(sessionKey: string) {
    return apiClient.get<OpenClawSessionUsage>('/openclaw/chat/usage', {
      params: { sessionKey },
    });
  },

  eventsUrl() {
    return `${BASE_URL}/openclaw/chat/events`;
  },
};
