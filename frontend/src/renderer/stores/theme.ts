import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';
import type { GlobalThemeOverrides } from 'naive-ui';

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const FONT_FAMILY =
  "'DM Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif";

const lightOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#0b7f78',
    primaryColorHover: '#0d9488',
    primaryColorPressed: '#0f5f5a',
    primaryColorSuppl: '#14b8a6',
    infoColor: '#0b7f78',
    infoColorHover: '#0d9488',
    infoColorPressed: '#0f5f5a',
    infoColorSuppl: '#14b8a6',
    successColor: '#059669',
    successColorHover: '#10b981',
    successColorPressed: '#047857',
    successColorSuppl: '#10b981',
    warningColor: '#d97706',
    warningColorHover: '#f59e0b',
    warningColorPressed: '#b45309',
    warningColorSuppl: '#f59e0b',
    errorColor: '#dc2626',
    errorColorHover: '#ef4444',
    errorColorPressed: '#b91c1c',
    errorColorSuppl: '#ef4444',
    borderRadius: '10px',
    borderRadiusSmall: '8px',
    fontFamily: FONT_FAMILY,
    fontFamilyMono: "'JetBrains Mono', 'Cascadia Code', Consolas, monospace",
    bodyColor: '#eef6f8',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
  },
  Card: {
    borderRadius: '12px',
  },
  Button: {
    borderRadiusMedium: '10px',
    borderRadiusSmall: '8px',
  },
  Menu: {
    borderRadius: '8px',
    itemColorActive: 'rgba(11, 127, 120, 0.12)',
    itemColorActiveHover: 'rgba(11, 127, 120, 0.18)',
    itemTextColorActive: '#0b7f78',
    itemTextColorActiveHover: '#0f5f5a',
    itemIconColorActive: '#0b7f78',
    itemIconColorActiveHover: '#0f5f5a',
  },
  Tag: {
    borderRadius: '8px',
  },
  Progress: {
    railColor: 'rgba(11, 127, 120, 0.12)',
  },
};

const darkOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#2dd4bf',
    primaryColorHover: '#5eead4',
    primaryColorPressed: '#14b8a6',
    primaryColorSuppl: '#5eead4',
    infoColor: '#2dd4bf',
    infoColorHover: '#5eead4',
    infoColorPressed: '#14b8a6',
    infoColorSuppl: '#5eead4',
    successColor: '#34d399',
    successColorHover: '#6ee7b7',
    successColorPressed: '#10b981',
    successColorSuppl: '#6ee7b7',
    warningColor: '#fbbf24',
    warningColorHover: '#fcd34d',
    warningColorPressed: '#f59e0b',
    warningColorSuppl: '#fcd34d',
    errorColor: '#f87171',
    errorColorHover: '#fca5a5',
    errorColorPressed: '#ef4444',
    errorColorSuppl: '#fca5a5',
    borderRadius: '10px',
    borderRadiusSmall: '8px',
    fontFamily: FONT_FAMILY,
    fontFamilyMono: "'JetBrains Mono', 'Cascadia Code', Consolas, monospace",
    bodyColor: '#0a0f18',
    cardColor: '#141c2b',
    modalColor: '#141c2b',
    popoverColor: '#141c2b',
    tableColor: '#141c2b',
  },
  Card: {
    borderRadius: '12px',
    color: '#141c2b',
    colorEmbedded: '#141c2b',
  },
  Button: {
    borderRadiusMedium: '10px',
    borderRadiusSmall: '8px',
  },
  Menu: {
    borderRadius: '8px',
    itemColorActive: 'rgba(45, 212, 191, 0.15)',
    itemColorActiveHover: 'rgba(45, 212, 191, 0.2)',
    itemTextColorActive: '#2dd4bf',
    itemTextColorActiveHover: '#5eead4',
    itemIconColorActive: '#2dd4bf',
    itemIconColorActiveHover: '#5eead4',
  },
  Tag: {
    borderRadius: '8px',
  },
  Progress: {
    railColor: 'rgba(45, 212, 191, 0.15)',
  },
};

export function getThemeOverrides(resolved: ResolvedTheme): GlobalThemeOverrides {
  return resolved === 'dark' ? darkOverrides : lightOverrides;
}

function getSystemTheme(): ResolvedTheme {
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export const useThemeStore = defineStore(
  'theme',
  () => {
    const mode = ref<ThemeMode>('system');
    const systemTheme = ref<ResolvedTheme>(getSystemTheme());

    const resolvedTheme = computed<ResolvedTheme>(() => {
      if (mode.value === 'system') return systemTheme.value;
      return mode.value;
    });

    const themeOverrides = computed(() => getThemeOverrides(resolvedTheme.value));

    let mediaQuery: MediaQueryList | null = null;
    let mediaHandler: ((e: MediaQueryListEvent) => void) | null = null;
    let initialized = false;

    function initTheme() {
      if (initialized) return;
      initialized = true;

      systemTheme.value = getSystemTheme();
      applyDataTheme(resolvedTheme.value);

      watch(resolvedTheme, (theme) => {
        applyDataTheme(theme);
      });

      if (typeof window === 'undefined') return;

      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      mediaHandler = (e: MediaQueryListEvent) => {
        systemTheme.value = e.matches ? 'dark' : 'light';
      };
      mediaQuery.addEventListener('change', mediaHandler);
    }

    function applyDataTheme(theme: ResolvedTheme) {
      if (typeof document === 'undefined') return;
      document.documentElement.setAttribute('data-theme', theme);
    }

    function setMode(next: ThemeMode) {
      mode.value = next;
      applyDataTheme(resolvedTheme.value);
    }

    function cycleMode() {
      const order: ThemeMode[] = ['system', 'light', 'dark'];
      const idx = order.indexOf(mode.value);
      setMode(order[(idx + 1) % order.length]);
    }

    function disposeThemeListener() {
      if (mediaQuery && mediaHandler) {
        mediaQuery.removeEventListener('change', mediaHandler);
      }
    }

    return {
      mode,
      resolvedTheme,
      themeOverrides,
      initTheme,
      setMode,
      cycleMode,
      disposeThemeListener,
    };
  },
  {
    persist: {
      paths: ['mode'],
    },
  },
);

// Legacy export for gradual migration
export const themeOverrides = lightOverrides;
