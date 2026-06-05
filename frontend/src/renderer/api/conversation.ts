import apiClient from './client';
import type {
  ChatMessage,
  Conversation,
  OpenClawSessionsResult,
  PageRequest,
  PageResult,
} from '@shared/types';

export const conversationApi = {
  listConversations(params: PageRequest) {
    return apiClient.get<PageResult<Conversation>>('/conversations', {
      params: {
        page: params.page - 1,
        size: params.pageSize,
        sort: params.sortBy ? `${params.sortBy},${params.sortOrder || 'asc'}` : undefined,
      },
    });
  },

  listOpenClawSessions(limit = 50, search?: string, includePreview = true) {
    return apiClient.get<OpenClawSessionsResult>('/conversations/openclaw/sessions', {
      params: { limit, search, includePreview },
    });
  },

  getOpenClawHistory(
    sessionKey: string,
    limit = 50,
    maxChars = 12_000,
    light = false,
  ) {
    return apiClient.get<ChatMessage[]>('/conversations/openclaw/history', {
      params: { sessionKey, limit, maxChars, light },
    });
  },

  syncFromOpenClaw() {
    return apiClient.post<Conversation[]>('/conversations/openclaw/sync');
  },

  getConversation(id: string) {
    return apiClient.get<Conversation>(`/conversations/${id}`);
  },

  createConversation(conversation: Partial<Conversation>) {
    return apiClient.post<Conversation>('/conversations', conversation);
  },

  updateConversation(id: string, conversation: Partial<Conversation>) {
    return apiClient.put<Conversation>(`/conversations/${id}`, conversation);
  },

  deleteConversation(id: string) {
    return apiClient.delete<void>(`/conversations/${id}`);
  },
};
