<template>
  <div
    class="msg-row"
    :class="[
      `role-${msg.role}`,
      { 'is-error': isError, 'is-thinking': msg.streaming && !hasContent },
    ]"
  >
    <div class="msg-avatar" :class="`avatar-${msg.role}`" aria-hidden="true">
      {{ roleInitial }}
    </div>
    <div class="msg-main">
      <div class="msg-label">
        <span class="msg-label-name">{{ roleLabel }}</span>
      </div>
      <div class="msg-bubble">
        <div v-if="msg.streaming && !hasContent" class="thinking-wrap">
          <span class="thinking-text">正在回复</span>
          <span class="thinking-dots"><span /><span /><span /></span>
        </div>
        <div v-else class="msg-parts">
          <template v-for="(part, idx) in displayParts" :key="`${msg.id}-part-${idx}`">
            <ChatPartText v-if="part.type === 'text'" :text="part.text" />
            <ChatPartImage
              v-else-if="part.type === 'image'"
              :part="part"
              :local-preview-url="msg.localPreviewUrl"
            />
            <ChatPartFile v-else-if="part.type === 'file'" :part="part" />
          </template>
          <div v-if="!displayParts.length && fallbackText" class="msg-text">{{ fallbackText }}</div>
        </div>
        <span v-if="msg.streaming && hasContent" class="stream-cursor">▍</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ChatPartFile from '@/components/chat/ChatPartFile.vue';
import ChatPartImage from '@/components/chat/ChatPartImage.vue';
import ChatPartText from '@/components/chat/ChatPartText.vue';
import { messageContent, normalizeChatParts, partsFingerprint } from '@/utils/chatParts';
import type { UiChatMessage } from '@shared/types';

const props = defineProps<{
  msg: UiChatMessage;
}>();

const displayParts = computed(() => normalizeChatParts(props.msg));

const fallbackText = computed(() => messageContent(props.msg));

const hasContent = computed(
  () => displayParts.value.length > 0 || Boolean(fallbackText.value.trim()),
);

const isError = computed(() => {
  if (props.msg.role !== 'assistant') return false;
  const t = fallbackText.value.trim();
  return (
    t.startsWith('⚠️') ||
    /^Agent failed/i.test(t) ||
    t.includes('No API key found') ||
    t.includes('Unknown model:')
  );
});

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    user: '你',
    assistant: 'OpenClaw',
    system: '系统',
    tool: '工具',
  };
  return map[props.msg.role] ?? props.msg.role;
});

const roleInitial = computed(() => {
  const map: Record<string, string> = {
    user: '我',
    assistant: 'OC',
    system: '系',
    tool: '工',
  };
  return map[props.msg.role] ?? '?';
});

defineExpose({
  partsFingerprint: computed(() => partsFingerprint(props.msg.parts)),
});
</script>

<style scoped>
.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  width: 100%;
  max-width: 100%;
}

.msg-row.role-user {
  flex-direction: row-reverse;
}

.msg-row.role-system,
.msg-row.role-tool {
  justify-content: center;
}

.msg-row.role-system .msg-avatar,
.msg-row.role-tool .msg-avatar {
  display: none;
}

.msg-row.role-system .msg-main,
.msg-row.role-tool .msg-main {
  max-width: min(640px, 92%);
  align-items: center;
}

.msg-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: -0.02em;
  user-select: none;
}

.avatar-assistant {
  background: linear-gradient(135deg, var(--oc-primary), var(--oc-accent-soft));
  color: #fff;
  box-shadow: 0 2px 8px var(--oc-glow);
}

.avatar-user {
  background: var(--oc-chat-composer-bg);
  border: 1.5px solid var(--oc-primary);
  color: var(--oc-primary);
}

.avatar-system {
  background: var(--oc-chat-msg-system-bg);
  color: var(--oc-chat-msg-system-text);
}

.avatar-tool {
  background: var(--oc-chat-msg-tool-bg);
  color: var(--oc-chat-msg-tool-text);
  font-family: var(--n-font-family-mono);
  font-size: 10px;
}

.msg-main {
  max-width: min(680px, calc(100% - 48px));
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.msg-row.role-user .msg-main {
  align-items: flex-end;
}

.msg-label {
  padding: 0 6px;
}

.msg-label-name {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--oc-chat-msg-muted);
}

.msg-row.role-user .msg-label-name {
  color: var(--oc-chat-msg-muted);
}

.msg-row.role-assistant .msg-label-name {
  color: var(--oc-primary);
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.7;
  letter-spacing: 0.01em;
  min-width: 48px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.msg-row.role-assistant .msg-bubble {
  background: var(--oc-chat-msg-assistant-bg);
  border: 1px solid var(--oc-chat-msg-assistant-border);
  color: var(--oc-chat-msg-text);
  border-bottom-left-radius: 6px;
}

.msg-row.role-user .msg-bubble {
  background: linear-gradient(145deg, var(--oc-primary) 0%, var(--oc-primary-hover) 100%);
  border: none;
  color: var(--oc-chat-msg-user-text);
  border-bottom-right-radius: 6px;
  box-shadow: 0 4px 14px var(--oc-glow);
}

.msg-row.role-user .msg-text,
.msg-row.role-user :deep(.part-text) {
  color: var(--oc-chat-msg-user-text);
}

.msg-row.role-assistant .msg-text,
.msg-row.role-assistant :deep(.part-text) {
  color: var(--oc-chat-msg-text);
}

.msg-row.role-system .msg-bubble {
  background: var(--oc-chat-msg-system-bg);
  border: 1px dashed var(--oc-chat-msg-assistant-border);
  color: var(--oc-chat-msg-system-text);
  font-size: 13px;
  text-align: center;
  border-radius: 12px;
}

.msg-row.role-tool .msg-bubble {
  background: var(--oc-chat-msg-tool-bg);
  border: 1px solid var(--oc-chat-msg-assistant-border);
  color: var(--oc-chat-msg-tool-text);
  font-size: 13px;
  font-family: var(--n-font-family-mono);
  border-radius: 10px;
}

.msg-row.is-error .msg-bubble {
  background: color-mix(in srgb, var(--oc-error) 12%, var(--oc-chat-msg-assistant-bg));
  border: 1px solid var(--oc-error);
  color: var(--oc-chat-msg-text);
}

.msg-row.is-error .msg-label-name {
  color: var(--oc-error);
}

.msg-row.is-thinking .msg-bubble {
  padding: 14px 18px;
  border-style: dashed;
}

.msg-parts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.stream-cursor {
  display: inline;
  margin-left: 1px;
  animation: blink 1s step-end infinite;
  color: var(--oc-primary);
  font-weight: 300;
}

.thinking-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.thinking-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--oc-chat-msg-muted);
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--oc-primary);
  animation: bounce 1.2s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

@media (max-width: 900px) {
  .msg-main {
    max-width: calc(100% - 42px);
  }
}
</style>
