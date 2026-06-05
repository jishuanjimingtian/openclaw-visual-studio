<template>
  <button
    ref="rootEl"
    type="button"
    class="part-image"
    :style="aspectStyle"
    @click="open = true"
  >
    <img
      v-if="visible"
      :src="src"
      :alt="name"
      loading="lazy"
      decoding="async"
      class="part-image-el"
    />
    <span v-else class="part-image-placeholder" />
  </button>

  <n-modal v-model:show="open" preset="card" :title="name" style="max-width: 92vw; width: 720px">
    <img :src="fullSrc" :alt="name" class="part-image-full" />
  </n-modal>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { NModal } from 'naive-ui';
import { chatAttachmentApi } from '@/api/chatAttachment';
import type { AttachmentPart } from '@shared/types';

const props = defineProps<{
  part: AttachmentPart;
  localPreviewUrl?: string;
}>();

const visible = ref(false);
const open = ref(false);
const rootEl = ref<HTMLButtonElement | null>(null);
let observer: IntersectionObserver | null = null;

const src = computed(() => {
  if (props.localPreviewUrl) return props.localPreviewUrl;
  if (props.part.attachmentId === 'inline-stripped') return '';
  if (props.part.thumbReady !== false) {
    return chatAttachmentApi.thumbUrl(props.part.attachmentId);
  }
  return chatAttachmentApi.attachmentUrl(props.part.attachmentId);
});

const fullSrc = computed(() => {
  if (props.localPreviewUrl) return props.localPreviewUrl;
  return chatAttachmentApi.attachmentUrl(props.part.attachmentId);
});

const name = computed(() => props.part.name || 'image');

const aspectStyle = computed(() => {
  const w = props.part.width ?? 4;
  const h = props.part.height ?? 3;
  return { aspectRatio: `${w} / ${h}` };
});

onMounted(() => {
  const el = rootEl.value;
  if (!el) {
    visible.value = true;
    return;
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        visible.value = true;
        observer?.disconnect();
      }
    },
    { rootMargin: '120px' },
  );
  observer.observe(el);
});

onBeforeUnmount(() => {
  observer?.disconnect();
});
</script>

<style scoped>
.part-image {
  display: block;
  max-width: min(320px, 100%);
  border: none;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
  border-radius: 12px;
  overflow: hidden;
}

.part-image-el,
.part-image-placeholder {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: cover;
  border-radius: 12px;
}

.part-image-placeholder {
  min-height: 120px;
  background: color-mix(in srgb, var(--oc-chat-msg-muted) 18%, transparent);
}

.part-image-full {
  display: block;
  max-width: 100%;
  max-height: 75vh;
  margin: 0 auto;
  object-fit: contain;
}
</style>
