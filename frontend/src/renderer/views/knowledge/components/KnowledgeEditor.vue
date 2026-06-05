<template>
  <div class="editor-wrap">
    <n-input
      v-model:value="localContent"
      type="textarea"
      class="editor-textarea"
      placeholder="Markdown 记忆内容…"
      @update:value="onEdit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
  content: string;
}>();

const emit = defineEmits<{
  'update:content': [value: string];
  dirty: [value: boolean];
}>();

const localContent = ref(props.content);

watch(() => props.content, (v) => {
  localContent.value = v;
});

function onEdit(v: string) {
  emit('update:content', v);
  emit('dirty', v !== props.content);
}
</script>

<style scoped>
.editor-wrap {
  flex: 1;
  min-height: 200px;
  display: flex;
  flex-direction: column;
}

.editor-wrap :deep(.n-input) {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.editor-wrap :deep(.n-input-wrapper) {
  height: 100%;
}

.editor-wrap :deep(textarea) {
  min-height: 200px;
  height: 100% !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: none;
}
</style>
