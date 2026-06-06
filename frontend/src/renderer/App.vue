<template>
  <n-config-provider
    :theme="naiveTheme"
    :theme-overrides="themeStore.themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <n-dialog-provider>
      <n-notification-provider>
        <n-message-provider>
          <ChatNotifyHost />
          <GatewayHealthNotifyHost />
          <AppUpdatePrompt />
          <div class="app-shell" :data-theme="themeStore.resolvedTheme">
            <div class="app-layout">
              <Sidebar />
              <div class="app-main">
                <AppHeader />
                <main class="app-content">
                  <div class="app-content-mesh" aria-hidden="true" />
                  <router-view v-slot="{ Component, route }">
                    <transition name="slide-fade" mode="out-in">
                      <keep-alive include="DeploymentView,DashboardView">
                        <component
                          v-if="Component"
                          :is="Component"
                          :key="route.path"
                        />
                      </keep-alive>
                    </transition>
                  </router-view>
                </main>
              </div>
            </div>
          </div>
        </n-message-provider>
      </n-notification-provider>
    </n-dialog-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue';
import { NConfigProvider, NDialogProvider, NNotificationProvider, NMessageProvider, darkTheme } from 'naive-ui';
import { zhCN, dateZhCN } from 'naive-ui';
import Sidebar from '@/components/common/Sidebar.vue';
import AppHeader from '@/components/common/AppHeader.vue';
import ChatNotifyHost from '@/components/chat/ChatNotifyHost.vue';
import GatewayHealthNotifyHost from '@/components/common/GatewayHealthNotifyHost.vue';
import AppUpdatePrompt from '@/components/AppUpdatePrompt.vue';
import { useThemeStore } from '@/stores/theme';
import { useGatewayStore } from '@/stores/gateway';
import { useOpenClawChatStore } from '@/stores/openclawChat';
import { useAppShutdown } from '@/composables/useAppShutdown';

const themeStore = useThemeStore();
const gatewayStore = useGatewayStore();
const openClawChatStore = useOpenClawChatStore();

useAppShutdown();

const naiveTheme = computed(() =>
  themeStore.resolvedTheme === 'dark' ? darkTheme : null,
);

onMounted(() => {
  gatewayStore.ensureConnected();
  void openClawChatStore.initGlobal();
});

onUnmounted(() => {
  themeStore.disposeThemeListener();
});
</script>

<style scoped>
.app-shell {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.app-content {
  position: relative;
  flex: 1;
  min-height: 0;
  min-width: 0;
  padding: 16px 24px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background-color: transparent;
}

.app-content-mesh {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 50% at 10% 20%, var(--oc-mesh-1), transparent 50%),
    radial-gradient(ellipse 60% 40% at 90% 80%, var(--oc-mesh-2), transparent 50%),
    radial-gradient(ellipse 50% 30% at 50% 50%, var(--oc-mesh-3), transparent 50%);
  animation: mesh-drift 24s ease-in-out infinite alternate;
}

@keyframes mesh-drift {
  0% { opacity: 0.85; }
  100% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .app-content-mesh {
    animation: none;
  }
}

.app-content > :not(.app-content-mesh) {
  position: relative;
  z-index: 1;
}

/* 让页面根节点占满主内容区，便于会话管理等内部滚动布局 */
.app-content > :not(.app-content-mesh):last-child {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition:
    opacity var(--oc-transition, 180ms ease),
    transform var(--oc-transition, 180ms ease);
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
