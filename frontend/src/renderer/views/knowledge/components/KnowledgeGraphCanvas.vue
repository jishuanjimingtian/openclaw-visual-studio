<template>
  <div class="graph-wrap knowledge-nebula" :class="{ compact: props.compact }">
    <div class="graph-grid" aria-hidden="true" />
    <div class="graph-glow" aria-hidden="true" />
    <div ref="chartEl" class="graph-canvas" />
    <div v-if="hiddenDailyHint" class="graph-window-hint">{{ hiddenDailyHint }}</div>
    <div v-if="!hasData" class="graph-empty">
      <div class="empty-orb" />
      <n-empty description="记忆星云待播种">
        <template #extra>
          <n-space vertical :size="8" align="center">
            <n-text class="empty-hint">创建 MEMORY.md 或今日日记，关系将在此浮现</n-text>
            <n-button size="small" type="primary" @click="$emit('seed')">
              新建长期记忆
            </n-button>
          </n-space>
        </template>
      </n-empty>
    </div>
    <div v-if="hasData" class="graph-toolbar">
      <n-button size="tiny" secondary @click="fitView">
        <template #icon><n-icon><scan-outline /></n-icon></template>
        适应画布
      </n-button>
    </div>
    <div class="graph-legend">
      <span v-for="item in legend" :key="item.kind" class="legend-item">
        <i class="legend-dot" :style="{ background: item.color }" />
        {{ item.label }}
      </span>
      <span v-if="stats" class="legend-stats">{{ stats }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed, nextTick } from 'vue';
import * as echarts from 'echarts';
import { NEmpty, NText, NButton, NSpace, NIcon } from 'naive-ui';
import { ScanOutline } from '@vicons/ionicons5';
import type { KnowledgeGraph } from '@shared/types';
import { useThemeStore } from '@/stores/theme';
import { buildKnowledgeGraphOption, graphStructureKey } from '../knowledge-graph.theme';
import { createKnowledgeGraphMotion } from '../knowledge-graph.motion';

const themeStore = useThemeStore();
const colorMode = computed(() =>
  (themeStore.resolvedTheme === 'light' ? 'light' : 'dark') as 'light' | 'dark',
);

const props = defineProps<{
  graph: KnowledgeGraph | null;
  highlightNodeIds?: string[];
  focusNodeId?: string | null;
  compact?: boolean;
}>();

const emit = defineEmits<{
  nodeClick: [nodeId: string];
  seed: [];
}>();

const chartEl = ref<HTMLElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let lastStructureKey = '';
let pendingStructureChange = false;

const legend = computed(() => {
  const light = colorMode.value === 'light';
  return [
    { kind: 'temporal', label: '时间', color: light ? '#0d9488' : '#3dd6c6' },
    { kind: 'link', label: '引用', color: light ? '#7c3aed' : '#8b6dff' },
    { kind: 'promote', label: '晋升', color: light ? '#d97706' : '#f59e0b' },
    { kind: 'tag', label: '标签', color: light ? '#2563eb' : '#5b8def' },
    { kind: 'semantic', label: '语义', color: light ? '#db2777' : '#ec4899' },
  ];
});

const hasData = computed(() => (props.graph?.nodes.length ?? 0) > 0);

const stats = computed(() => {
  if (!props.graph?.nodes.length) return '';
  return `${props.graph.nodes.length} 节点 · ${props.graph.edges.length} 关系`;
});

const hiddenDailyHint = computed(() => {
  const hidden = props.graph?.meta.hiddenDailyCount ?? 0;
  if (hidden <= 0) return '';
  const days = props.graph?.meta.dailyWindowDays ?? 90;
  return `星云图仅显示近 ${days} 天日记（另有 ${hidden} 篇在侧栏）`;
});

const reducedMotion = computed(() =>
  typeof window !== 'undefined'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
);

const graphMotion = createKnowledgeGraphMotion(() => hasData.value && !reducedMotion.value);

const structureKey = computed(() => graphStructureKey(props.graph));

const overlayKey = computed(() =>
  `${(props.highlightNodeIds ?? []).join(',')}|${props.focusNodeId ?? ''}|${colorMode.value}`,
);

function graphOptions(structureChanged = false) {
  if (!props.graph) return null;
  return buildKnowledgeGraphOption(props.graph, {
    highlightNodeIds: props.highlightNodeIds,
    focusNodeId: props.focusNodeId,
    reducedMotion: reducedMotion.value,
    colorMode: colorMode.value,
    structureChanged,
  });
}

async function renderFull() {
  await nextTick();
  if (!chartEl.value) return;
  if (!props.graph?.nodes.length) {
    graphMotion.unbind();
    chart?.clear();
    lastStructureKey = '';
    pendingStructureChange = false;
    return;
  }
  if (!chart) {
    chart = echarts.init(chartEl.value, undefined, { renderer: 'canvas' });
    chart.on('click', (params) => {
      if (params.dataType === 'node' && params.data && typeof params.data === 'object') {
        const id = (params.data as { id?: string }).id;
        if (id) emit('nodeClick', id);
      }
    });
    graphMotion.bind(chart, chartEl.value);
  }
  const structureChanged = pendingStructureChange;
  pendingStructureChange = false;
  graphMotion.onLayoutStart();
  const option = graphOptions(structureChanged);
  if (!option) return;
  chart.setOption(option, { notMerge: true });
  lastStructureKey = structureKey.value;
  requestAnimationFrame(() => chart?.resize());
}

async function renderOverlay() {
  if (!chart || !props.graph?.nodes.length) return;
  const option = graphOptions();
  if (!option) return;
  chart.setOption(option, { lazyUpdate: true });
  if (!reducedMotion.value) {
    requestAnimationFrame(() => graphMotion.refreshSnapshots());
  }
}

function fitView() {
  if (!chart || !props.graph?.nodes.length) return;
  chart.resize();
}

function handleResize() {
  chart?.resize();
}

watch(structureKey, (next, prev) => {
  if (prev && next !== prev) {
    pendingStructureChange = true;
  }
  renderFull();
});

watch(overlayKey, () => {
  if (structureKey.value !== lastStructureKey) {
    renderFull();
  } else {
    renderOverlay();
  }
});

onMounted(() => {
  renderFull();
  if (chartEl.value) {
    resizeObserver = new ResizeObserver(() => handleResize());
    resizeObserver.observe(chartEl.value);
  }
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  graphMotion.unbind();
  resizeObserver?.disconnect();
  window.removeEventListener('resize', handleResize);
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.graph-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  border-radius: 0;
  overflow: hidden;
  border: 1px solid var(--oc-knowledge-graph-border);
  box-shadow:
    inset 0 0 60px var(--oc-knowledge-graph-glow),
    var(--oc-shadow-card);
  background: var(--oc-knowledge-graph-bg);
}

.graph-window-hint {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 3;
  font-size: 11px;
  color: var(--oc-knowledge-detail-muted);
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--oc-knowledge-graph-legend-bg);
  border: 1px solid var(--oc-knowledge-graph-legend-border);
}

.graph-wrap.compact {
  min-height: 200px;
  height: 200px;
  border-radius: var(--oc-radius-md, 10px);
}

.graph-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--oc-knowledge-graph-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--oc-knowledge-graph-grid) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(ellipse 70% 70% at 50% 45%, black 20%, transparent 75%);
  pointer-events: none;
  animation: grid-drift 24s linear infinite;
}

.graph-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse 55% 45% at 50% 42%, var(--oc-knowledge-graph-glow), transparent 70%);
  pointer-events: none;
  animation: glow-pulse 4s ease-in-out infinite alternate;
}

@media (prefers-reduced-motion: reduce) {
  .graph-grid,
  .graph-glow {
    animation: none;
  }
}

@keyframes grid-drift {
  0% { transform: translate(0, 0); }
  100% { transform: translate(32px, 32px); }
}

@keyframes glow-pulse {
  0% { opacity: 0.6; }
  100% { opacity: 1; }
}

.graph-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.graph-empty {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}

.empty-orb {
  position: absolute;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 1px dashed var(--oc-knowledge-graph-border);
  animation: orb-spin 12s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .empty-orb {
    animation: none;
  }
}

@keyframes orb-spin {
  to { transform: rotate(360deg); }
}

.graph-toolbar {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  padding: 2px 4px;
  border-radius: 8px;
  background: var(--oc-knowledge-graph-legend-bg);
  backdrop-filter: blur(10px);
  border: 1px solid var(--oc-knowledge-graph-legend-border);
  box-shadow: var(--oc-shadow-card);
}

.empty-hint {
  font-size: 13px;
  color: var(--oc-knowledge-detail-muted) !important;
}

.graph-legend {
  position: absolute;
  right: 12px;
  bottom: 10px;
  z-index: 3;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--oc-knowledge-graph-legend-bg);
  backdrop-filter: blur(10px);
  border: 1px solid var(--oc-knowledge-graph-legend-border);
  font-size: 11px;
  color: var(--oc-knowledge-graph-legend-text);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  box-shadow: 0 0 6px currentColor;
}

.legend-stats {
  margin-left: 4px;
  color: var(--oc-knowledge-graph-label);
  font-weight: 500;
}
</style>
