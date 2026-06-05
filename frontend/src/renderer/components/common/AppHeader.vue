<template>
  <header class="app-header">
    <!-- 左：页面标识 -->
    <div class="header-leading">
      <div v-if="pageIcon" class="header-page-icon" aria-hidden="true">
        <n-icon :size="20">
          <component :is="pageIcon" />
        </n-icon>
      </div>
      <div class="header-copy" :class="{ 'has-desc': Boolean(pageDescription) }">
        <h1 class="header-title">
          <span class="header-title-text">{{ pageTitle }}</span>
        </h1>
        <p v-if="pageDescription" class="header-desc">
          <span class="header-desc-accent" aria-hidden="true" />
          <span class="header-desc-text">{{ pageDescription }}</span>
        </p>
      </div>
    </div>

    <!-- 右：页面操作 + 全局工具 -->
    <div class="header-trailing">
      <div
        id="app-header-actions"
        class="header-actions"
        :class="{ 'has-content': hasToolbarContent }"
      />
      <div
        class="header-split"
        aria-hidden="true"
        :class="{ visible: hasToolbarContent }"
      />
      <nav class="header-utilities" aria-label="全局操作">
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" class="util-btn" @click="themeStore.cycleMode()">
              <template #icon>
                <n-icon><component :is="themeIcon" /></n-icon>
              </template>
            </n-button>
          </template>
          {{ themeTooltip }}
        </n-tooltip>
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" class="util-btn" @click="openDocs">
              <template #icon>
                <n-icon><BookOutline /></n-icon>
              </template>
            </n-button>
          </template>
          文档
        </n-tooltip>
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button quaternary circle size="small" class="util-btn" @click="openFeedback">
              <template #icon>
                <n-icon><ChatboxOutline /></n-icon>
              </template>
            </n-button>
          </template>
          反馈
        </n-tooltip>
      </nav>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { APP_NAME_ZH } from '@shared/brand';
import { NButton, NIcon, NTooltip } from 'naive-ui';
import {
  BookOutline, ChatboxOutline,
  SunnyOutline, MoonOutline, DesktopOutline,
} from '@vicons/ionicons5';
import { useThemeStore } from '@/stores/theme';
import { headerDescriptionOverride } from '@/composables/appHeader';
import { getRouteIcon } from '@/utils/routeIcons';

const route = useRoute();
const themeStore = useThemeStore();
const hasToolbarContent = ref(false);
let toolbarObserver: MutationObserver | null = null;

const pageTitle = computed(() => (route.meta?.title as string) || APP_NAME_ZH);
const pageIcon = computed(() => getRouteIcon(route.meta?.icon as string | undefined));

const pageDescription = computed(() => {
  if (headerDescriptionOverride.value !== undefined) {
    return headerDescriptionOverride.value;
  }
  const meta = route.meta?.description;
  return typeof meta === 'string' && meta.length > 0 ? meta : undefined;
});

const themeIcon = computed(() => {
  if (themeStore.mode === 'light') return SunnyOutline;
  if (themeStore.mode === 'dark') return MoonOutline;
  return DesktopOutline;
});

const themeTooltip = computed(() => {
  const labels = { system: '跟随系统', light: '浅色模式', dark: '深色模式' };
  return `主题：${labels[themeStore.mode]}（点击切换）`;
});

function syncToolbarPresence() {
  const el = document.getElementById('app-header-actions');
  hasToolbarContent.value = Boolean(el && el.childElementCount > 0);
}

onMounted(() => {
  syncToolbarPresence();
  const el = document.getElementById('app-header-actions');
  if (el) {
    toolbarObserver = new MutationObserver(syncToolbarPresence);
    toolbarObserver.observe(el, { childList: true, subtree: true });
  }
});

onUnmounted(() => {
  toolbarObserver?.disconnect();
});

function openDocs() {
  window.open('https://docs.openclaw.org', '_blank');
}

function openFeedback() {
  window.open('https://github.com/openclaw/openclaw-vs/issues', '_blank');
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-shrink: 0;
  min-height: 54px;
  padding: 10px 22px;
  background: var(--oc-header-bg);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
  border-bottom: 1px solid var(--oc-header-border);
  box-shadow: var(--oc-header-shadow);
  position: relative;
  z-index: 5;
}

.app-header::after {
  content: '';
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--oc-primary) 40%, transparent) 0%,
    color-mix(in srgb, var(--oc-accent) 30%, transparent) 50%,
    transparent 100%
  );
  pointer-events: none;
}

/* —— 左侧：图标 + 文案 —— */
.header-leading {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.header-page-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: var(--oc-primary);
  background: var(--oc-header-icon-bg);
  border: 1px solid var(--oc-header-icon-border);
  box-shadow: 0 2px 10px var(--oc-glow);
}

.header-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  min-width: 0;
}

.header-copy.has-desc {
  gap: 4px;
}

.header-title {
  margin: 0;
  line-height: 1.25;
  min-width: 0;
}

.header-title-text {
  display: block;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: linear-gradient(
    105deg,
    var(--oc-primary) 0%,
    color-mix(in srgb, var(--oc-primary) 75%, var(--oc-accent)) 50%,
    var(--oc-accent) 100%
  );
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-desc {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  min-width: 0;
  line-height: 1.3;
}

.header-desc-accent {
  flex-shrink: 0;
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--oc-primary), var(--oc-accent));
  opacity: 0.85;
}

.header-desc-text {
  font-size: 12px;
  font-weight: 450;
  color: var(--oc-header-desc-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* —— 右侧：操作 + 工具 —— */
.header-trailing {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  max-width: min(58%, 640px);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  flex: 1;
}

.header-actions:not(.has-content) {
  display: none;
  width: 0;
  overflow: hidden;
}

.header-actions.has-content {
  padding: 4px 6px 4px 10px;
  border-radius: 10px;
  background: var(--oc-header-actions-bg);
  border: 1px solid var(--oc-header-actions-border);
}

.header-split {
  flex-shrink: 0;
  width: 1px;
  height: 26px;
  margin: 0;
  background: var(--oc-header-border);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--oc-transition);
}

.header-split.visible {
  opacity: 1;
}

.header-utilities {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding: 3px;
  border-radius: 999px;
  background: var(--oc-header-util-bg);
  border: 1px solid var(--oc-header-util-border);
}

.util-btn {
  --n-width: 30px;
  --n-height: 30px;
}
</style>

<style>
[data-theme='dark'] .header-title-text {
  background: linear-gradient(105deg, var(--oc-primary-hover) 0%, var(--oc-accent) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
</style>
