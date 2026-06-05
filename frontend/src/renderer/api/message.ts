import apiClient from './client';
import type { Message, PageRequest, PageResult } from '@shared/types';

export const messageApi = {
  listMessages(conversationId: string, params: PageRequest) {
    return apiClient.get<PageResult<Message>>(`/conversations/${conversationId}/messages`, {
      params: {
        page: params.page - 1,
        size: params.pageSize,
        sort: params.sortBy ? `${params.sortBy},${params.sortOrder || 'asc'}` : undefined,
      },
    });
  },

  getMessage(conversationId: string, messageId: string) {
    return apiClient.get<Message>(`/conversations/${conversationId}/messages/${messageId}`);
  },

  createMessage(conversationId: string, message: Partial<Message>) {
    return apiClient.post<Message>(`/conversations/${conversationId}/messages`, message);
  },

  updateMessage(conversationId: string, messageId: string, message: Partial<Message>) {
    return apiClient.put<Message>(`/conversations/${conversationId}/messages/${messageId}`, message);
  },

  deleteMessage(conversationId: string, messageId: string) {
    return apiClient.delete<void>(`/conversations/${conversationId}/messages/${messageId}`);
  },
};