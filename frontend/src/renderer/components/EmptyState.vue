<template>
  <div class="empty-state">
    <n-empty :description="description">
      <template v-if="$slots.extra" #extra>
        <slot name="extra" />
      </template>
    </n-empty>
  </div>
</template>

<script setup lang="ts">
import { NEmpty } from 'naive-ui';

withDefaults(defineProps<{
  description?: string;
}>(), {
  description: '暂无数据',
});
</script>

<style scoped>
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
}

.empty-state :deep(.n-empty .n-empty__icon) {
  color: var(--oc-primary);
}
</style>