<template>
  <div v-if="hits.length" class="search-hits">
    <span class="search-label">
      {{ fallback ? '关键词' : '语义' }} · {{ hits.length }} 条
    </span>
    <button
      v-for="(hit, i) in hits"
      :key="i"
      type="button"
      class="search-hit"
      @click="$emit('select', hit)"
    >
      <span class="hit-path">{{ hit.path }}</span>
      <span class="hit-snippet">{{ hit.snippet }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { KnowledgeSearchHit } from '@shared/types';

defineProps<{
  hits: KnowledgeSearchHit[];
  fallback?: boolean;
}>();

defineEmits<{ select: [hit: KnowledgeSearchHit] }>();
</script>

<style scoped>
.search-hits {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
}

.search-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--oc-knowledge-detail-muted);
  margin-right: 4px;
}

.search-hit {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 220px;
  padding: 6px 10px;
  border: 1px solid var(--oc-knowledge-graph-border);
  border-radius: 8px;
  background: var(--oc-surface-elevated);
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  transition: border-color 0.15s, background 0.15s;
}

.search-hit:hover {
  border-color: var(--oc-primary);
  background: var(--oc-chat-session-active);
}

.hit-path {
  font-size: 11px;
  font-weight: 600;
  font-family: ui-monospace, Menlo, monospace;
  color: var(--oc-knowledge-detail-title);
}

.hit-snippet {
  font-size: 11px;
  margin-top: 2px;
  color: var(--oc-knowledge-detail-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}
</style>
