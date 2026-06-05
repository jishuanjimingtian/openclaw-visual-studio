<template>
  <aside class="sidebar">
    <div class="sidebar-logo">
      <LogoMark class="logo-icon" />
      <div v-show="!collapsed" class="logo-brand">
        <span class="logo-text-zh">{{ APP_NAME_ZH }}</span>
        <span class="logo-text-en">{{ APP_NAME_EN }}</span>
      </div>
    </div>
    <n-menu
      :value="currentRoute"
      :options="menuOptions"
      :collapsed="collapsed"
      :collapsed-width="64"
      @update:value="onMenuClick"
    />
    <div class="sidebar-footer">
      <n-button
        quaternary
        circle
        size="small"
        @click="collapsed = !collapsed"
      >
        <template #icon>
          <n-icon>
            <ChevronBack v-if="!collapsed" />
            <ChevronForward v-else />
          </n-icon>
        </template>
      </n-button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, h } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { NMenu, NButton, NIcon } from 'naive-ui';
import type { MenuOption } from 'naive-ui';
import LogoMark from '@/components/common/LogoMark.vue';
import { APP_NAME_ZH, APP_NAME_EN } from '@shared/brand';
import { ChevronBack, ChevronForward } from '@vicons/ionicons5';
import { getRouteIcon } from '@/utils/routeIcons';

const router = useRouter();
const route = useRoute();
const collapsed = ref(false);

const currentRoute = computed(() => route.path);

function renderIcon(iconName: string) {
  const iconComponent = getRouteIcon(iconName);
  if (!iconComponent) return undefined;
  return () => h(NIcon, null, { default: () => h(iconComponent) });
}

const menuOptions = computed<MenuOption[]>(() =>
  router.options.routes
    .filter((r) => r.meta?.title)
    .map((r) => ({
      label: r.meta?.title as string,
      key: r.path,
      icon: renderIcon(r.meta?.icon as string),
    })),
);

function onMenuClick(key: string) {
  router.push(key);
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: v-bind("collapsed ? '68px' : '232px'");
  min-width: v-bind("collapsed ? '68px' : '232px'");
  height: 100%;
  align-self: stretch;
  background: linear-gradient(180deg, var(--oc-sidebar-from) 0%, var(--oc-sidebar-to) 100%);
  border-right: 1px solid var(--oc-sidebar-border);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  transition:
    width var(--oc-transition),
    min-width var(--oc-transition);
  overflow: hidden;
  z-index: 10;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  height: 68px;
  padding: 0 v-bind("collapsed ? '12px' : '16px'");
  gap: 12px;
  border-bottom: 1px solid var(--oc-sidebar-border);
  position: relative;
}

.sidebar-logo::after {
  content: '';
  position: absolute;
  inset: 6px 10px auto;
  height: v-bind("collapsed ? '40px' : '48px'");
  border-radius: 12px;
  background: radial-gradient(ellipse at center, var(--oc-glow), transparent 70%);
  pointer-events: none;
  animation: logo-glow 3s ease-in-out infinite alternate;
}

@media (prefers-reduced-motion: reduce) {
  .sidebar-logo::after {
    animation: none;
  }
}

@keyframes logo-glow {
  0% { opacity: 0.5; }
  100% { opacity: 1; }
}

.logo-icon {
  width: v-bind("collapsed ? '40px' : '44px'");
  height: v-bind("collapsed ? '40px' : '44px'");
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  padding: 4px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--oc-primary) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--oc-primary) 18%, transparent);
  box-shadow: 0 4px 16px var(--oc-glow);
}

.logo-brand {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.logo-text-zh {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
  line-height: 1.2;
  background: linear-gradient(135deg, var(--oc-primary) 0%, var(--oc-accent) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.logo-text-en {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.22em;
  color: var(--oc-muted);
  opacity: 0.92;
  padding-left: 2px;
}

.sidebar :deep(.n-menu) {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.sidebar :deep(.n-menu-item-content--selected) {
  position: relative;
}

.sidebar :deep(.n-menu-item-content) {
  border-radius: var(--oc-radius-md);
  margin: 2px 0;
}

.sidebar :deep(.n-menu-item-content--selected) {
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--oc-primary) 20%, transparent);
}

.sidebar :deep(.n-menu-item-content--selected::before) {
  content: '';
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 55%;
  border-radius: 4px;
  background: linear-gradient(180deg, var(--oc-primary), var(--oc-accent));
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--n-border-color);
  display: flex;
  justify-content: center;
}
</style>
