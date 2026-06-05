<template>
  <n-card
    class="dash-stat-card oc-stat-card"
    :class="[`dash-stat-card--${tone}`, { 'dash-stat-card--hero': hero }]"
    size="small"
    :hoverable="Boolean(onClick)"
    :bordered="false"
    @click="onClick?.()"
  >
    <div class="dash-stat-inner">
      <div class="dash-stat-icon" :style="{ '--stat-accent': accent }">
        <n-icon size="22"><component :is="icon" /></n-icon>
      </div>
      <div class="dash-stat-body">
        <div class="dash-stat-value" :class="{ 'dash-stat-value--lg': hero }">{{ value }}</div>
        <div class="dash-stat-label">{{ label }}</div>
        <div v-if="sub" class="dash-stat-sub">{{ sub }}</div>
      </div>
    </div>
  </n-card>
</template>

<script setup lang="ts">
import { NCard, NIcon } from 'naive-ui';
import type { Component } from 'vue';

withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    sub?: string;
    icon: Component;
    tone?: 'primary' | 'success' | 'warning' | 'accent' | 'metric' | 'neutral';
    accent?: string;
    hero?: boolean;
    onClick?: () => void;
  }>(),
  { tone: 'primary' },
);
</script>

<style scoped>
.dash-stat-card {
  cursor: default;
  background: var(--oc-stat-bg);
  border: 1px solid var(--oc-stat-border);
  box-shadow: var(--oc-shadow-card);
  overflow: hidden;
  position: relative;
}

.dash-stat-card::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 3px;
  background: var(--stat-bar, linear-gradient(90deg, var(--oc-primary), var(--oc-accent)));
  opacity: 0.85;
}

.dash-stat-card--primary { --stat-bar: linear-gradient(90deg, var(--oc-primary), var(--oc-primary-hover)); }
.dash-stat-card--success { --stat-bar: linear-gradient(90deg, var(--oc-success), var(--oc-success-light)); }
.dash-stat-card--warning { --stat-bar: linear-gradient(90deg, var(--oc-warning), var(--oc-warning-light)); }
.dash-stat-card--accent { --stat-bar: linear-gradient(90deg, var(--oc-accent), var(--oc-accent-soft)); }
.dash-stat-card--metric { --stat-bar: linear-gradient(90deg, var(--oc-metric), var(--oc-metric-light)); }
.dash-stat-card--neutral { --stat-bar: linear-gradient(90deg, var(--oc-muted), color-mix(in srgb, var(--oc-muted) 60%, transparent)); }

.dash-stat-card[hoverable]:hover {
  cursor: pointer;
}

.dash-stat-inner {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.dash-stat-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: color-mix(in srgb, var(--stat-accent, var(--oc-primary)) 14%, transparent);
  color: var(--stat-accent, var(--oc-primary));
  border: 1px solid color-mix(in srgb, var(--stat-accent, var(--oc-primary)) 22%, transparent);
}

.dash-stat-value {
  font-size: 1.35rem;
  font-weight: 700;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: var(--dashboard-text, var(--oc-session-text));
}

.dash-stat-value--lg {
  font-size: 1.65rem;
  background: linear-gradient(135deg, var(--oc-primary-pressed) 0%, var(--oc-accent) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

[data-theme='dark'] .dash-stat-value--lg {
  background: linear-gradient(135deg, var(--oc-primary-hover) 0%, var(--oc-accent-soft) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.dash-stat-label {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--dashboard-text-secondary, var(--oc-session-text-secondary));
}

.dash-stat-sub {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.35;
  color: var(--dashboard-text-muted, var(--oc-session-text-muted));
}
</style>
