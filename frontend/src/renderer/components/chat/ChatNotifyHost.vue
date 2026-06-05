<template>
  <span class="chat-notify-host" aria-hidden="true" />
</template>

<script setup lang="ts">
import { watch, onMounted, h } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useNotification, NButton } from 'naive-ui';
import { useOpenClawChatStore } from '@/stores/openclawChat';
import {
  loadChatNotifyPrefs,
  notifyQueue,
  notifyTick,
  setNotifyRoutePath,
  showDesktopNotification,
  type ChatReplyNotifyItem,
} from '@/utils/chatNotify';

const router = useRouter();
const route = useRoute();
const notification = useNotification();
const chatStore = useOpenClawChatStore();

const NOTIFY_DURATION_MS = 5000;
let lastProcessedIndex = 0;

function openChatSession(item: ChatReplyNotifyItem) {
  chatStore.selectSessionByKey(item.sessionKey);
  void router.push('/chat');
}

function showInAppNotify(item: ChatReplyNotifyItem) {
  const title = item.isError ? 'OpenClaw 回复出错' : 'OpenClaw 已回复';
  const meta = item.sessionTitle;

  notification.create({
    title,
    content: item.preview,
    meta,
    duration: NOTIFY_DURATION_MS,
    keepAliveOnHover: true,
    action: () =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          onClick: () => openChatSession(item),
        },
        { default: () => '打开对话' },
      ),
  });

  showDesktopNotification(
    `${title} · ${item.sessionTitle}`,
    item.preview,
    () => openChatSession(item),
  );
}

function processNewItems() {
  const queue = notifyQueue.value;
  while (lastProcessedIndex < queue.length) {
    showInAppNotify(queue[lastProcessedIndex]);
    lastProcessedIndex += 1;
  }
  if (lastProcessedIndex > 0 && lastProcessedIndex >= queue.length) {
    notifyQueue.value = [];
    lastProcessedIndex = 0;
  }
}

watch(notifyTick, () => processNewItems());

watch(
  () => route.path,
  (path) => setNotifyRoutePath(path),
  { immediate: true },
);

onMounted(() => {
  loadChatNotifyPrefs();
  setNotifyRoutePath(route.path);
  processNewItems();
});
</script>

<style scoped>
.chat-notify-host {
  display: none;
}
</style>
