<template>
  <div
    class="composer-shell"
    :class="{
      'is-dragover': dragOver,
      'has-attachments': queue.pending.length > 0,
      'is-disabled': disabled,
    }"
    @dragenter.prevent="onDragEnter"
    @dragover.prevent="onDragOver"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
  >
    <div v-if="dragOver && !disabled" class="drop-overlay" aria-hidden="true">
      <span class="drop-icon">📎</span>
      <span class="drop-text">释放以添加图片或文档</span>
    </div>

    <div v-if="queue.pending.length" class="attachment-strip">
      <div
        v-for="item in queue.pending"
        :key="item.id"
        class="attachment-chip"
        :class="[`state-${item.state}`]"
      >
        <div class="chip-preview">
          <img
            v-if="item.mime.startsWith('image/')"
            :src="item.previewUrl"
            alt=""
            class="chip-thumb"
          />
          <div v-else class="chip-file">
            <n-icon size="22"><DocumentTextOutline /></n-icon>
          </div>
          <div v-if="item.state === 'uploading'" class="chip-progress">
            <n-progress
              type="circle"
              :percentage="item.progress"
              :show-indicator="false"
              :stroke-width="8"
              :size="44"
            />
          </div>
        </div>
        <div class="chip-info">
          <span class="chip-name" :title="item.name">{{ item.name }}</span>
          <span class="chip-meta">{{ chipMeta(item) }}</span>
        </div>
        <button
          type="button"
          class="chip-remove"
          aria-label="移除附件"
          :disabled="item.state === 'uploading'"
          @click="remove(item.id)"
        >
          ×
        </button>
      </div>
    </div>

    <div class="composer-body">
      <div class="composer-input-box">
        <slot />
      </div>
    </div>

    <div class="composer-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="attach-btn"
          title="添加图片或文档"
          :disabled="disabled"
          @click="pickFiles"
        >
          <n-icon size="18"><AttachOutline /></n-icon>
        </button>
        <span class="toolbar-hint">Enter 发送 · Shift+Enter 换行</span>
      </div>
      <div class="toolbar-right">
        <slot name="actions" />
      </div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      class="hidden-input"
      multiple
      :accept="accept"
      @change="onFileInput"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { NIcon, NProgress, useMessage } from 'naive-ui';
import { AttachOutline, DocumentTextOutline } from '@vicons/ionicons5';
import { ChatUploadQueue, type PendingAttachment } from '@/utils/chatUploadQueue';

const props = defineProps<{
  queue: ChatUploadQueue;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  change: [];
}>();

const message = useMessage();
const dragOver = ref(false);
const dragDepth = ref(0);
const fileInputRef = ref<HTMLInputElement | null>(null);

const accept = 'image/*,.pdf,.txt,.md,.csv,text/plain,text/markdown,text/csv,application/pdf';

function pickFiles() {
  fileInputRef.value?.click();
}

function chipMeta(item: PendingAttachment) {
  const size =
    item.sizeBytes >= 1024 * 1024
      ? `${(item.sizeBytes / (1024 * 1024)).toFixed(1)} MB`
      : item.sizeBytes >= 1024
        ? `${(item.sizeBytes / 1024).toFixed(0)} KB`
        : `${item.sizeBytes} B`;
  if (item.state === 'uploading') return `上传中 ${item.progress}%`;
  if (item.state === 'error') return item.error || '上传失败';
  if (item.state === 'ready') return `已就绪 · ${size}`;
  return size;
}

function onDragEnter() {
  if (props.disabled) return;
  dragDepth.value += 1;
  dragOver.value = true;
}

function onDragOver() {
  if (props.disabled) return;
  dragOver.value = true;
}

function onDragLeave() {
  if (props.disabled) return;
  dragDepth.value = Math.max(0, dragDepth.value - 1);
  if (dragDepth.value === 0) dragOver.value = false;
}

async function ingest(files: FileList | File[] | null | undefined) {
  if (!files || props.disabled) return;
  try {
    await props.queue.addFiles(files);
    emit('change');
  } catch (e) {
    message.error(e instanceof Error ? e.message : '无法添加附件');
  }
}

async function onFileInput(ev: Event) {
  const input = ev.target as HTMLInputElement;
  await ingest(input.files);
  input.value = '';
}

async function onDrop(ev: DragEvent) {
  dragDepth.value = 0;
  dragOver.value = false;
  await ingest(ev.dataTransfer?.files);
}

function remove(id: string) {
  props.queue.remove(id);
  emit('change');
}

defineExpose({
  async addFromClipboard(files: FileList | File[]) {
    await ingest(files);
  },
});
</script>

<style scoped>
.composer-shell {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid var(--oc-chat-input-border);
  background: var(--oc-chat-thread-bg);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color var(--oc-transition),
    box-shadow var(--oc-transition),
    background var(--oc-transition);
}

.composer-shell:focus-within {
  border-color: var(--oc-primary);
  box-shadow: 0 0 0 3px var(--oc-glow);
}

.composer-shell.is-dragover {
  border-color: var(--oc-primary);
  box-shadow: 0 0 0 3px var(--oc-glow);
}

.composer-shell.is-disabled {
  opacity: 0.72;
}

.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: inherit;
  background: color-mix(in srgb, var(--oc-primary) 10%, var(--oc-chat-thread-bg) 90%);
  border: 2px dashed var(--oc-primary);
  pointer-events: none;
}

.drop-icon {
  font-size: 28px;
  line-height: 1;
}

.drop-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--oc-primary);
}

.attachment-strip {
  display: flex;
  gap: 8px;
  padding: 10px 12px 0;
  overflow-x: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}

.attachment-chip {
  position: relative;
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 220px;
  padding: 8px;
  border-radius: 12px;
  border: 1px solid var(--oc-chat-msg-assistant-border);
  background: var(--oc-chat-composer-bg);
}

.attachment-chip.state-error {
  border-color: color-mix(in srgb, var(--oc-error) 50%, var(--oc-chat-msg-assistant-border));
}

.chip-preview {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 10px;
  overflow: hidden;
  background: color-mix(in srgb, var(--oc-chat-msg-muted) 10%, transparent);
}

.chip-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.chip-file {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oc-primary);
}

.chip-progress {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--oc-chat-panel-bg) 55%, transparent);
}

.chip-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chip-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--oc-chat-msg-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-meta {
  font-size: 11px;
  color: var(--oc-chat-msg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-chip.state-error .chip-meta {
  color: var(--oc-error);
}

.chip-remove {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--oc-chat-msg-muted);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--oc-transition),
    color var(--oc-transition);
}

.chip-remove:hover:not(:disabled) {
  background: color-mix(in srgb, var(--oc-error) 12%, transparent);
  color: var(--oc-error);
}

.chip-remove:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.composer-body {
  padding: 8px 12px 0;
}

.composer-input-box {
  border: 1px solid var(--oc-chat-input-border);
  border-radius: 10px;
  background: var(--oc-chat-composer-bg);
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color var(--oc-transition),
    box-shadow var(--oc-transition),
    background var(--oc-transition);
}

.composer-input-box:focus-within {
  border-color: var(--oc-primary);
  background: var(--oc-chat-thread-bg);
  box-shadow:
    inset 0 1px 2px rgba(15, 23, 42, 0.03),
    0 0 0 2px color-mix(in srgb, var(--oc-primary) 14%, transparent);
}

.composer-body :deep(.n-input) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

.composer-body :deep(.n-input__border),
.composer-body :deep(.n-input__state-border) {
  display: none !important;
}

.composer-body :deep(.n-input__textarea-el) {
  font-size: 15px;
  line-height: 1.65;
  color: var(--oc-chat-msg-text) !important;
  padding: 10px 12px 8px !important;
  min-height: 56px;
}

.composer-body :deep(.n-input__placeholder) {
  color: var(--oc-chat-msg-muted) !important;
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px 10px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.attach-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--oc-chat-msg-assistant-border);
  border-radius: 10px;
  background: var(--oc-chat-composer-bg);
  color: var(--oc-chat-msg-muted);
  cursor: pointer;
  transition:
    border-color var(--oc-transition),
    color var(--oc-transition),
    background var(--oc-transition),
    box-shadow var(--oc-transition);
}

.attach-btn:hover:not(:disabled) {
  color: var(--oc-primary);
  border-color: var(--oc-primary-hover);
  background: color-mix(in srgb, var(--oc-primary) 8%, var(--oc-chat-composer-bg));
}

.attach-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.toolbar-hint {
  font-size: 11px;
  color: var(--oc-chat-msg-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.hidden-input {
  display: none;
}

@media (max-width: 900px) {
  .toolbar-hint {
    display: none;
  }

  .attachment-chip {
    width: 188px;
  }
}
</style>
