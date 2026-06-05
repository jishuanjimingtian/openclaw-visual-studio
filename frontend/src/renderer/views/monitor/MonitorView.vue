<template>
  <div class="page">
    <HeaderToolbar>
      <n-button size="small" @click="refreshAll">
        <template #icon><refresh-outline /></template>
        刷新
      </n-button>
    </HeaderToolbar>

    <div class="page-body">
    <!-- 指标卡片 -->
    <div class="metrics-row">
      <n-card v-for="metric in metricCards" :key="metric.label" class="metric-card oc-stat-card">
        <div class="metric-card-inner">
          <div class="metric-icon" :style="{ color: metric.color }">
            <n-icon size="32"><component :is="metric.icon" /></n-icon>
          </div>
          <div class="metric-info">
            <span class="metric-value">{{ metric.value }}</span>
            <span class="metric-label">{{ metric.label }}</span>
          </div>
        </div>
      </n-card>
    </div>

    <!-- CPU / 内存趋势图 -->
    <n-card title="资源趋势" class="chart-card" style="margin-bottom: 16px">
      <n-grid cols="2" x-gap="16">
        <n-grid-item>
          <div class="chart-section">
            <h4>CPU 使用率</h4>
            <div class="bar-chart" ref="cpuChartRef">
              <div
                v-for="(point, idx) in cpuHistory"
                :key="idx"
                class="bar"
                :style="{ height: point.value + '%' }"
                :title="`${point.time}: ${point.value}%`"
              >
                <span class="bar-label">{{ point.value }}%</span>
              </div>
            </div>
          </div>
        </n-grid-item>
        <n-grid-item>
          <div class="chart-section">
            <h4>内存使用率</h4>
            <div class="bar-chart" ref="memChartRef">
              <div
                v-for="(point, idx) in memHistory"
                :key="idx"
                class="bar memory-bar"
                :style="{ height: point.value + '%' }"
                :title="`${point.time}: ${point.value}%`"
              >
                <span class="bar-label">{{ point.value }}%</span>
              </div>
            </div>
          </div>
        </n-grid-item>
      </n-grid>
    </n-card>

    <!-- 详细信息 -->
    <n-grid cols="2" x-gap="16">
      <n-grid-item>
        <n-card title="系统概览">
          <n-descriptions :column="1" label-placement="left" bordered size="small">
            <n-descriptions-item label="CPU 使用率">
              <n-progress type="line" :percentage="cpuPercent" :color="cpuColor" :indicator="false" />
              <span style="margin-left: 8px">{{ cpuPercent }}%</span>
            </n-descriptions-item>
            <n-descriptions-item label="内存使用率">
              <n-progress type="line" :percentage="memPercent" :color="memColor" :indicator="false" />
              <span style="margin-left: 8px">{{ memPercent }}%</span>
            </n-descriptions-item>
            <n-descriptions-item label="磁盘使用率">
              <n-progress type="line" :percentage="diskPercent" :color="diskColor" :indicator="false" />
              <span style="margin-left: 8px">{{ diskPercent }}%</span>
            </n-descriptions-item>
            <n-descriptions-item label="运行时间">{{ uptime }}</n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card title="会话统计">
          <n-descriptions :column="1" label-placement="left" bordered size="small">
            <n-descriptions-item label="总会话数">
              <n-tag type="success">{{ stats?.totalConversations || 0 }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="活跃模型数">
              <n-tag type="info">{{ stats?.activeModels || 0 }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="部署中任务数">
              <n-tag :type="(stats?.activeDeployments || 0) > 0 ? 'warning' : 'default'">
                {{ stats?.activeDeployments || 0 }}
              </n-tag>
            </n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-grid-item>
    </n-grid>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import {
  NCard, NGrid, NGridItem, NButton, NIcon, NProgress, NDescriptions, NDescriptionsItem, NTag,
} from 'naive-ui';
import { RefreshOutline, ServerOutline, CloudOutline, GridOutline } from '@vicons/ionicons5';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import { useDashboardStore } from '@/stores/dashboard';
import { ocColors, progressColor } from '@/utils/chartColors';

const store = useDashboardStore();

const cpuHistory = ref<{ time: string; value: number }[]>([]);
const memHistory = ref<{ time: string; value: number }[]>([]);

const cpuPercent = computed(() => store.metrics.cpu);
const memPercent = computed(() => store.metrics.memory);
const diskPercent = computed(() => store.metrics.disk);
const uptime = computed(() => formatUptime(store.metrics.uptime));
const stats = computed(() => store.stats);

const cpuColor = computed(() => progressColor(cpuPercent.value, 80, 50));
const memColor = computed(() => progressColor(memPercent.value, 85, 60));
const diskColor = computed(() => ocColors.metric);

const metricCards = computed(() => [
  {
    label: 'CPU',
    value: `${cpuPercent.value}%`,
    icon: ServerOutline,
    color: cpuColor.value,
  },
  {
    label: '内存',
    value: `${memPercent.value}%`,
    icon: ServerOutline,
    color: memColor.value,
  },
  {
    label: '磁盘',
    value: `${diskPercent.value}%`,
    icon: CloudOutline,
    color: ocColors.metric,
  },
  {
    label: '活跃会话',
    value: `${stats.value?.totalConversations || 0}`,
    icon: GridOutline,
    color: ocColors.primary,
  },
]);

let pollTimer: ReturnType<typeof setInterval> | null = null;

function formatUptime(seconds: number): string {
  if (!seconds) return '-';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  return `${days}天 ${hours}时 ${mins}分`;
}

// function formatTokens(tokens: number): string {
//   if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(1)}M`;
//   if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(1)}K`;
//   return String(tokens);
// }

function appendHistoryFromStore() {
  const m = store.metrics;
  if (m.cpu <= 0 && m.memory <= 0) return;
  const now = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  cpuHistory.value.push({ time: now, value: m.cpu });
  memHistory.value.push({ time: now, value: m.memory });
  if (cpuHistory.value.length > 20) cpuHistory.value = cpuHistory.value.slice(-20);
  if (memHistory.value.length > 20) memHistory.value = memHistory.value.slice(-20);
}

async function refreshAll() {
  await Promise.allSettled([
    store.fetchAllProgressive(),
    store.pollLocalSystemMetrics(),
  ]);
  appendHistoryFromStore();
}

onMounted(() => {
  store.startLocalMetricsPolling(3000);
  refreshAll();
  pollTimer = setInterval(() => {
    store.refreshLight();
    appendHistoryFromStore();
  }, 10000);
});

onUnmounted(() => {
  store.stopLocalMetricsPolling();
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.metric-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}
.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--n-color-modal);
}
.metric-info {
  display: flex;
  flex-direction: column;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--n-text-color);
}
.metric-label {
  font-size: 13px;
  color: var(--n-text-color-3);
  margin-top: 4px;
}
.chart-card {
  margin-bottom: 16px;
}
.chart-section {
  padding: 12px 0;
}
.chart-section h4 {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--n-text-color-2);
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 160px;
  padding: 4px;
  background: var(--n-color-modal);
  border-radius: 6px;
}
.bar {
  flex: 1;
  background: linear-gradient(to top, var(--oc-success), var(--oc-success-light));
  border-radius: 2px 2px 0 0;
  min-height: 4px;
  position: relative;
  transition: height 0.5s ease;
}
.memory-bar {
  background: linear-gradient(to top, var(--oc-primary), var(--oc-primary-hover));
}
.bar-label {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9px;
  color: var(--n-text-color-3);
  white-space: nowrap;
}
</style>