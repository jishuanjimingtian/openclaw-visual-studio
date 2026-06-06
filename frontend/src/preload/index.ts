import { contextBridge, ipcRenderer } from 'electron';
import type { LocalSystemMetrics } from '@shared/types';
import type {
  AppUpdateCheckOptions,
  AppUpdateEvent,
  AppUpdateState,
} from '@shared/update';

const UPDATE_CHANNEL = 'update:event';

contextBridge.exposeInMainWorld('electronAPI', {
  getVersion: () => ipcRenderer.invoke('app:getVersion'),
  getPath: (name: string) => ipcRenderer.invoke('app:getPath', name),
  getBackendUrl: () => ipcRenderer.invoke('app:getBackendUrl'),
  isPackaged: () => ipcRenderer.invoke('app:isPackaged'),
  prepareQuit: () => ipcRenderer.invoke('app:prepareQuit') as Promise<void>,
  onPrepareQuit: (listener: () => void) => {
    const handler = () => listener();
    ipcRenderer.on('app:prepare-quit', handler);
    return () => ipcRenderer.removeListener('app:prepare-quit', handler);
  },
  getSystemMetrics: () => ipcRenderer.invoke('system:getMetrics') as Promise<LocalSystemMetrics>,
  showDesktopNotification: (payload: { title: string; body: string }) =>
    ipcRenderer.invoke('notification:show', payload) as Promise<boolean>,
  hashChatFile: (filePath: string) => ipcRenderer.invoke('chat:hash-file', filePath) as Promise<string>,
  statChatFile: (filePath: string) =>
    ipcRenderer.invoke('chat:stat-file', filePath) as Promise<{ size: number; isFile: boolean }>,
  platform: process.platform,
  update: {
    getState: () => ipcRenderer.invoke('update:getState') as Promise<AppUpdateState>,
    check: (options?: AppUpdateCheckOptions) =>
      ipcRenderer.invoke('update:check', options) as Promise<AppUpdateState>,
    download: () => ipcRenderer.invoke('update:download') as Promise<AppUpdateState>,
    dismiss: () => ipcRenderer.invoke('update:dismiss') as Promise<AppUpdateState>,
    quitAndInstall: () => ipcRenderer.invoke('update:quitAndInstall') as Promise<void>,
    onEvent: (listener: (event: AppUpdateEvent) => void) => {
      const handler = (_event: unknown, payload: AppUpdateEvent) => listener(payload);
      ipcRenderer.on(UPDATE_CHANNEL, handler);
      return () => {
        ipcRenderer.removeListener(UPDATE_CHANNEL, handler);
      };
    },
  },
});
