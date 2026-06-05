import { defineStore } from 'pinia';
import { ref } from 'vue';
import { DEFAULT_BACKEND_PORT } from '@shared/backend';
import type { ThemeMode } from '@/stores/theme';

export type AppLang = 'zh-CN' | 'en-US';

export const DEFAULT_GATEWAY_PORT = 18789;

export interface AppSettingsExportV1 {
  version: 1;
  theme: ThemeMode;
  lang: AppLang;
  backendPort: number;
  gatewayPort: number;
  chatNotify: { enabled: boolean; desktop: boolean };
  exportedAt: string;
}

export const useAppSettingsStore = defineStore(
  'appSettings',
  () => {
    const lang = ref<AppLang>('zh-CN');
    const backendPort = ref(DEFAULT_BACKEND_PORT);
    const gatewayPort = ref(DEFAULT_GATEWAY_PORT);

    function setLang(next: AppLang) {
      lang.value = next;
    }

    function setBackendPort(port: number) {
      backendPort.value = Math.min(65535, Math.max(1024, Math.round(port)));
    }

    function setGatewayPort(port: number) {
      gatewayPort.value = Math.min(65535, Math.max(1024, Math.round(port)));
    }

    function resetDefaults() {
      lang.value = 'zh-CN';
      backendPort.value = DEFAULT_BACKEND_PORT;
      gatewayPort.value = DEFAULT_GATEWAY_PORT;
    }

    function applySnapshot(partial: Partial<AppSettingsExportV1>) {
      if (partial.lang === 'zh-CN' || partial.lang === 'en-US') {
        lang.value = partial.lang;
      }
      if (typeof partial.backendPort === 'number' && !Number.isNaN(partial.backendPort)) {
        setBackendPort(partial.backendPort);
      }
      if (typeof partial.gatewayPort === 'number' && !Number.isNaN(partial.gatewayPort)) {
        setGatewayPort(partial.gatewayPort);
      }
    }

    return {
      lang,
      backendPort,
      gatewayPort,
      setLang,
      setBackendPort,
      setGatewayPort,
      resetDefaults,
      applySnapshot,
    };
  },
  {
    persist: {
      paths: ['lang', 'backendPort', 'gatewayPort'],
    },
  },
);
