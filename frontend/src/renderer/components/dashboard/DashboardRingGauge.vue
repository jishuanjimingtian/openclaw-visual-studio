<template>
  <button
    type="button"
    class="dash-ring-gauge"
    :class="{ 'dash-ring-gauge--clickable': Boolean(onClick) }"
    :style="{ '--ring-size': `${size}px` }"
    @click="onClick?.()"
  >
    <div class="dash-ring-gauge__chart">
      <n-progress
        type="circle"
        :percentage="percentage"
        :color="color"
        :rail-color="railColor"
        :stroke-width="7"
        :size="size"
        :show-indicator="false"
      />
      <div class="dash-ring-gauge__center">
        <span class="dash-ring-gauge__value">{{ display }}</span>
      </div>
    </div>
    <span class="dash-ring-gauge__label">{{ label }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NProgress } from 'naive-ui';
import { useThemeStore } from '@/stores/theme';

const themeStore = useThemeStore();

const railColor = computed(() =>
  themeStore.resolvedTheme === 'dark'
    ? 'color-mix(in srgb, var(--oc-session-text-muted) 32%, transparent)'
    : 'color-mix(in srgb, var(--oc-session-text-muted) 24%, transparent)',
);

withDefaults(
  defineProps<{
    label: string;
    display: string;
    percentage: number;
    color: string;
    size?: number;
    onClick?: () => void;
  }>(),
  { size: 76 },
);
</script>

<style scoped>
.dash-ring-gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 2px;
  border: none;
  background: transparent;
  cursor: default;
  min-width: 0;
  width: 100%;
}

.dash-ring-gauge--clickable {
  cursor: pointer;
  border-radius: 10px;
  transition: background-color var(--oc-transition);
}

.dash-ring-gauge--clickable:hover {
  background: color-mix(in srgb, var(--oc-primary) 6%, transparent);
}

.dash-ring-gauge__chart {
  position: relative;
  width: var(--ring-size);
  height: var(--ring-size);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dash-ring-gauge__center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.dash-ring-gauge__value {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--dashboard-text, var(--oc-session-text));
  line-height: 1;
}

.dash-ring-gauge__label {
  font-size: 12px;
  font-weight: 500;
  color: var(--dashboard-text-secondary, var(--oc-session-text-secondary));
  text-align: center;
  line-height: 1.3;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
