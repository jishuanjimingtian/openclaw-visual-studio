/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

interface ElectronAPI {
  getVersion: () => Promise<string>;
  getPath: (name: string) => Promise<string>;
  getBackendUrl: () => Promise<string>;
  isPackaged: () => Promise<boolean>;
  getSystemMetrics?: () => Promise<import('@shared/types').LocalSystemMetrics>;
  showDesktopNotification?: (payload: { title: string; body: string }) => Promise<boolean>;
  hashChatFile?: (filePath: string) => Promise<string>;
  statChatFile?: (filePath: string) => Promise<{ size: number; isFile: boolean }>;
  platform: string;
  prepareQuit?: () => Promise<void>;
  onPrepareQuit?: (listener: () => void) => () => void;
  update?: {
    getState: () => Promise<import('@shared/update').AppUpdateState>;
    check: (options?: import('@shared/update').AppUpdateCheckOptions) => Promise<import('@shared/update').AppUpdateState>;
    download: () => Promise<import('@shared/update').AppUpdateState>;
    dismiss: () => Promise<import('@shared/update').AppUpdateState>;
    quitAndInstall: () => Promise<void>;
    onEvent: (listener: (event: import('@shared/update').AppUpdateEvent) => void) => () => void;
  };
}

interface Window {
  electronAPI: ElectronAPI;
}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string;
    description?: string;
    icon?: string;
  }
}
