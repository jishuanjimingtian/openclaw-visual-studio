<template>
  <Teleport to="#app-header-actions" :disabled="!teleportActive">
    <div v-if="teleportActive" class="header-toolbar">
      <slot />
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onActivated, onDeactivated } from 'vue';

/** keep-alive 缓存页面 deactivate 后 Teleport 内容不会自动移除，需手动停用 */
const teleportActive = ref(true);

onActivated(() => {
  teleportActive.value = true;
});

onDeactivated(() => {
  teleportActive.value = false;
});
</script>

<style scoped>
.header-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: nowrap;
  min-width: 0;
}

.header-toolbar :deep(.n-button) {
  --n-border-radius: 8px;
}

.header-toolbar :deep(.n-select) {
  flex-shrink: 0;
}

.header-toolbar :deep(.refresh-hint) {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--oc-muted);
  white-space: nowrap;
}

.header-toolbar :deep(.n-input) {
  flex-shrink: 0;
}
</style>
