<template>
  <n-card size="small" class="health-panel oc-stat-card" :bordered="false">
    <template #header>
      <n-text strong>系统状态</n-text>
    </template>
    <template #header-extra>
      <n-button text type="primary" size="tiny" @click="$router.push('/monitor')">
        监控详情
      </n-button>
    </template>
    <div class="health-rings">
      <DashboardRingGauge
        v-for="item in items"
        :key="item.label"
        :label="item.label"
        :display="item.display"
        :percentage="item.percentage"
        :color="item.color"
        :on-click="item.onClick"
      />
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { NCard, NButton, NText } from 'naive-ui';
import DashboardRingGauge from './DashboardRingGauge.vue';

export interface HealthRingItem {
  label: string;
  display: string;
  percentage: number;
  color: string;
  onClick?: () => void;
}

defineProps<{
  items: HealthRingItem[];
}>();
</script>

<style scoped>
.health-panel {
  background: var(--oc-stat-bg) !important;
  border: 1px solid var(--oc-stat-border) !important;
  box-shadow: var(--oc-shadow-card);
}

.health-panel :deep(.n-card-header) {
  padding-bottom: 8px;
}

.health-panel :deep(.n-card-header__main) {
  color: var(--dashboard-heading, var(--oc-config-heading, var(--oc-session-text)));
}

.health-panel :deep(.n-card-header__extra) {
  color: var(--dashboard-text-secondary, var(--oc-session-text-secondary));
}

.health-rings {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 4px;
  justify-items: center;
}

@media (max-width: 900px) {
  .health-rings {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
