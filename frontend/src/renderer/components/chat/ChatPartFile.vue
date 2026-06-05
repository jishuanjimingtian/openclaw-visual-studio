<template>
  <a
    class="part-file"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
  >
    <span class="part-file-icon">📄</span>
    <span class="part-file-meta">
      <span class="part-file-name">{{ part.name }}</span>
      <span class="part-file-size">{{ sizeLabel }}</span>
    </span>
  </a>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { chatAttachmentApi } from '@/api/chatAttachment';
import type { AttachmentPart } from '@shared/types';

const props = defineProps<{ part: AttachmentPart }>();

const href = computed(() => chatAttachmentApi.attachmentUrl(props.part.attachmentId));

const sizeLabel = computed(() => {
  const n = props.part.sizeBytes;
  if (!n) return '';
  if (n >= 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  if (n >= 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${n} B`;
});
</script>

<style scoped>
.part-file {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--oc-chat-msg-assistant-border);
  background: var(--oc-chat-composer-bg);
  color: var(--oc-chat-msg-text);
  text-decoration: none;
  max-width: min(360px, 100%);
}

.part-file:hover {
  border-color: var(--oc-primary);
}

.part-file-icon {
  font-size: 20px;
  line-height: 1;
}

.part-file-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.part-file-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.part-file-size {
  font-size: 11px;
  color: var(--oc-chat-msg-muted);
}
</style>
