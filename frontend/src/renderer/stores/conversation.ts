import { defineStore } from 'pinia';
import { ref } from 'vue';
import { conversationApi } from '@/api/conversation';
import { mapHistoryToUi } from '@/utils/chatHistory';
import type {
  ChatMessage,
  Conversation,
  OpenClawSession,
  OpenClawSessionsResult,
  PageRequest,
} from '@shared/types';

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref<Conversation[]>([]);
  const openclawSessions = ref<OpenClawSession[]>([]);
  const openclawMeta = ref<OpenClawSessionsResult | null>(null);
  const selectedSessionKey = ref<string | null>(null);
  const chatMessages = ref<ChatMessage[]>([]);
  const currentConversation = ref<Conversation | null>(null);
  const loading = ref(false);
  const historyLoading = ref(false);
  const historyPreview = ref(true);
  const totalElements = ref(0);
  const totalPages = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(20);

  async function fetchOpenClawSessions(search?: string, includePreview = true) {
    loading.value = true;
    try {
      const res = await conversationApi.listOpenClawSessions(50, search, includePreview);
      openclawMeta.value = res.data;
      openclawSessions.value = res.data.sessions;
      return res.data;
    } finally {
      loading.value = false;
    }
  }

  async function fetchSessionHistory(
    sessionKey: string,
    options?: { limit?: number; maxChars?: number; preview?: boolean },
  ) {
    const preview = options?.preview ?? historyPreview.value;
    const limit = options?.limit ?? (preview ? 30 : 80);
    const maxChars = options?.maxChars ?? (preview ? 8_000 : 12_000);
    historyLoading.value = true;
    selectedSessionKey.value = sessionKey;
    try {
      const res = await conversationApi.getOpenClawHistory(sessionKey, limit, maxChars, preview);
      chatMessages.value = mapHistoryToUi(res.data).map(({ role, content, timestamp }) => ({
        role,
        content,
        timestamp,
      }));
      historyPreview.value = preview;
      return res.data;
    } finally {
      historyLoading.value = false;
    }
  }

  async function fetchFullSessionHistory(sessionKey: string) {
    return fetchSessionHistory(sessionKey, { limit: 80, maxChars: 12_000, preview: false });
  }

  async function syncFromOpenClaw() {
    loading.value = true;
    try {
      const res = await conversationApi.syncFromOpenClaw();
      conversations.value = res.data;
      await fetchOpenClawSessions();
      return res.data;
    } finally {
      loading.value = false;
    }
  }

  async function fetchConversations(page = 1, size = 20) {
    loading.value = true;
    try {
      const params: PageRequest = { page, pageSize: size, sortBy: 'updatedAt', sortOrder: 'desc' };
      const res = await conversationApi.listConversations(params);
      conversations.value = res.data.content;
      totalElements.value = res.data.totalElements;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.number + 1;
      pageSize.value = res.data.size;
    } finally {
      loading.value = false;
    }
  }

  async function fetchConversation(id: string) {
    loading.value = true;
    try {
      const res = await conversationApi.getConversation(id);
      currentConversation.value = res.data;
      return res.data;
    } finally {
      loading.value = false;
    }
  }

  async function createConversation(conversation: Partial<Conversation>) {
    const res = await conversationApi.createConversation(conversation);
    conversations.value.unshift(res.data);
    return res.data;
  }

  async function updateConversation(id: string, conversation: Partial<Conversation>) {
    const res = await conversationApi.updateConversation(id, conversation);
    const idx = conversations.value.findIndex((c: Conversation) => c.id === id);
    if (idx !== -1) conversations.value[idx] = res.data;
    if (currentConversation.value?.id === id) currentConversation.value = res.data;
    return res.data;
  }

  async function deleteConversation(id: string) {
    await conversationApi.deleteConversation(id);
    conversations.value = conversations.value.filter((c: Conversation) => c.id !== id);
    if (currentConversation.value?.id === id) currentConversation.value = null;
  }

  function selectSession(session: OpenClawSession, preview = true) {
    selectedSessionKey.value = session.key;
    fetchSessionHistory(session.key, { preview });
  }

  return {
    conversations,
    openclawSessions,
    openclawMeta,
    selectedSessionKey,
    chatMessages,
    currentConversation,
    loading,
    historyLoading,
    historyPreview,
    totalElements,
    totalPages,
    currentPage,
    pageSize,
    fetchOpenClawSessions,
    fetchSessionHistory,
    fetchFullSessionHistory,
    syncFromOpenClaw,
    fetchConversations,
    fetchConversation,
    createConversation,
    updateConversation,
    deleteConversation,
    selectSession,
  };
});
