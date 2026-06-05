import type { AppSettingsExportV1, AppLang } from '@/stores/appSettings';
import { DEFAULT_GATEWAY_PORT, useAppSettingsStore } from '@/stores/appSettings';
import type { ThemeMode } from '@/stores/theme';
import { useThemeStore } from '@/stores/theme';
import { DEFAULT_BACKEND_PORT } from '@shared/backend';
import {
  getChatNotifyPrefs,
  loadChatNotifyPrefs,
  setChatNotifyDesktop,
  setChatNotifyEnabled,
} from '@/utils/chatNotify';

const OPENCLAW_SESSION_PREFIXES = ['opencl', 'ocvs-'] as const;

export function buildSettingsExport(): AppSettingsExportV1 {
  const themeStore = useThemeStore();
  const appStore = useAppSettingsStore();
  loadChatNotifyPrefs();
  const notify = getChatNotifyPrefs();
  return {
    version: 1,
    theme: themeStore.mode,
    lang: appStore.lang,
    backendPort: appStore.backendPort,
    gatewayPort: appStore.gatewayPort,
    chatNotify: { enabled: notify.enabled, desktop: notify.desktop },
    exportedAt: new Date().toISOString(),
  };
}

export function downloadSettingsJson(filename?: string) {
  const data = buildSettingsExport();
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download =
    filename ?? `openclaw-vs-settings-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

export function applySettingsImport(raw: unknown): string | null {
  if (!raw || typeof raw !== 'object') return '无效的配置文件';
  const obj = raw as Record<string, unknown>;

  const themeStore = useThemeStore();
  const appStore = useAppSettingsStore();

  const theme = obj.theme as ThemeMode | undefined;
  if (theme === 'light' || theme === 'dark' || theme === 'system') {
    themeStore.setMode(theme);
  }

  const lang = obj.lang as AppLang | undefined;
  if (lang === 'zh-CN' || lang === 'en-US') {
    appStore.setLang(lang);
  }

  if (typeof obj.backendPort === 'number') {
    appStore.setBackendPort(obj.backendPort);
  }
  if (typeof obj.gatewayPort === 'number') {
    appStore.setGatewayPort(obj.gatewayPort);
  }

  const chatNotify = obj.chatNotify as { enabled?: boolean; desktop?: boolean } | undefined;
  if (chatNotify && typeof chatNotify === 'object') {
    if (typeof chatNotify.enabled === 'boolean') {
      setChatNotifyEnabled(chatNotify.enabled);
    }
    if (typeof chatNotify.desktop === 'boolean') {
      setChatNotifyDesktop(chatNotify.desktop);
    }
  }

  return null;
}

/** 清除会话/UI 缓存，保留主题与已持久化的应用偏好 */
export function clearOpenClawSessionCaches(): number {
  let removed = 0;
  for (let i = sessionStorage.length - 1; i >= 0; i--) {
    const key = sessionStorage.key(i);
    if (!key) continue;
    if (OPENCLAW_SESSION_PREFIXES.some((p) => key.startsWith(p))) {
      sessionStorage.removeItem(key);
      removed += 1;
    }
  }
  return removed;
}

export function resetAppSettingsToDefaults() {
  const themeStore = useThemeStore();
  const appStore = useAppSettingsStore();
  themeStore.setMode('system');
  appStore.resetDefaults();
  appStore.setBackendPort(DEFAULT_BACKEND_PORT);
  appStore.setGatewayPort(DEFAULT_GATEWAY_PORT);
  setChatNotifyEnabled(true);
  setChatNotifyDesktop(true);
  clearOpenClawSessionCaches();
}

export function isValidSettingsExport(data: unknown): data is AppSettingsExportV1 {
  if (!data || typeof data !== 'object') return false;
  const o = data as AppSettingsExportV1;
  return o.version === 1 && typeof o.exportedAt === 'string';
}
