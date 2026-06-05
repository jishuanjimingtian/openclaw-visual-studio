<template>
  <div class="gateway-status-bar">
    <n-card size="small" :bordered="true" class="gwb-card">
      <div class="gwb-inner">
        <div class="gwb-leading">
          <span class="gwb-status-dot" :class="dotClass" />
          <div class="gwb-info">
            <div class="gwb-title-row">
              <n-text strong>{{ gatewayStore.statusLabel }}</n-text>
              <n-tag v-if="gatewayStore.port" size="small" round :bordered="false" class="gwb-tag">
                :{{ gatewayStore.port }}
              </n-tag>
              <n-tag v-if="gatewayStore.wsConnected" size="small" type="success" round :bordered="false" class="gwb-tag">
                RPC
              </n-tag>
              <n-tag v-if="gatewayStore.managedByLabel" size="small" type="info" round :bordered="false" class="gwb-tag">
                {{ gatewayStore.managedByLabel }}
              </n-tag>
            </div>
            <n-text v-if="subtitle" depth="3" class="gwb-subtitle">{{ subtitle }}</n-text>
          </div>
        </div>

        <div class="gwb-actions">
          <slot name="actions" />
          <template v-if="showStartStop">
            <n-button
              v-if="!gatewayStore.processOnline"
              size="small"
              type="primary"
              :loading="starting"
              @click="$emit('start')"
            >
              {{ starting ? '启动中…' : '启动 Gateway' }}
            </n-button>
            <n-button
              v-if="gatewayStore.processOnline && showStopButton"
              size="small"
              type="error"
              secondary
              :loading="stopping"
              @click="$emit('stop')"
            >
              停止
            </n-button>
          </template>
        </div>
      </div>

      <div v-if="gatewayStore.isStartupInProgress" class="gwb-startup-bar">
        <n-progress
          type="line"
          :percentage="gatewayStore.startupProgress"
          :height="6"
          :border-radius="3"
          :show-indicator="false"
        />
        <n-text depth="3" class="gwb-startup-text">{{ gatewayStore.startupMessage }}</n-text>
      </div>

      <!-- Warning alert -->
      <n-alert
        v-if="!gatewayStore.processOnline && !gatewayStore.isStartupInProgress"
        type="warning"
        :show-icon="false"
        class="gwb-alert"
      >
        Gateway 未运行。
        <template v-if="connectionHint">{{ connectionHint }}</template>
        <template v-else>
          请启动 <code>openclaw gateway</code> 或点击「启动 Gateway」。
        </template>
      </n-alert>

      <!-- Error alert -->
      <n-alert
        v-if="error"
        type="error"
        :show-icon="false"
        class="gwb-alert"
        closable
        @close="$emit('clearError')"
      >
        {{ error }}
      </n-alert>

      <slot name="extra" />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NAlert, NButton, NCard, NProgress, NTag, NText } from 'naive-ui';
import { useGatewayStore } from '@/stores/gateway';

withDefaults(defineProps<{
  subtitle?: string;
  connectionHint?: string;
  error?: string;
  showStartStop?: boolean;
  showStopButton?: boolean;
}>(), {
  showStartStop: true,
  showStopButton: true,
});

defineEmits<{
  start: [];
  stop: [];
  clearError: [];
}>();

const gatewayStore = useGatewayStore();

const starting = computed(() => gatewayStore.isStarting || gatewayStore.isStartupInProgress);
const stopping = computed(() => gatewayStore.isStopping);

const dotClass = computed(() => {
  if (gatewayStore.isHealthy) return 'on';
  if (gatewayStore.wsConnected) return 'on';
  if (gatewayStore.isStarting) return 'starting';
  if (gatewayStore.isError) return 'err';
  return 'off';
});
</script>

<style scoped>
.gateway-status-bar {
  margin-bottom: 12px;
}
.gwb-card {
  --n-padding-top: 14px;
  --n-padding-bottom: 14px;
}
.gwb-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.gwb-leading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.gwb-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--n-text-color-disabled);
  transition: background .2s;
}
.gwb-status-dot.on { background: #22c55e; }
.gwb-status-dot.starting { background: #eab308; animation: gwb-pulse 1.2s ease-in-out infinite; }
.gwb-status-dot.err { background: #ef4444; }
.gwb-status-dot.off { background: #9ca3af; }
@keyframes gwb-pulse {
  0%,100% { opacity: 1; }
  50% { opacity: .35; }
}
.gwb-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.gwb-tag { font-size: 11px; }
.gwb-subtitle {
  font-size: 12px;
  margin-top: 2px;
  display: block;
}
.gwb-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.gwb-startup-bar {
  margin-top: 12px;
}
.gwb-startup-text {
  font-size: 12px;
  margin-top: 4px;
  display: block;
}
.gwb-alert {
  margin-top: 10px;
}
</style>
