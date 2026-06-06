import { createHash } from 'crypto';
import { createReadStream, promises as fs } from 'fs';
import { app, BrowserWindow, dialog, ipcMain, Menu, Notification, nativeImage, shell } from 'electron';
import { join } from 'path';
import { APP_WINDOW_TITLE, APP_PRODUCT_NAME } from '@shared/brand';
import { DEFAULT_BACKEND_PORT, getApiBaseUrl } from '@shared/backend';
import { startBackend, prepareAppQuit } from './backendManager';
import { collectLocalSystemMetrics, warmCpuSampler } from './systemMetrics';
import { disposeUpdateManager, initUpdateManager, registerUpdateHandlers } from './updateManager';

let mainWindow: BrowserWindow | null = null;
let quitting = false;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1024,
    minHeight: 680,
    title: APP_WINDOW_TITLE,
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
    show: false,
  });

  mainWindow.setMenuBarVisibility(false);

  mainWindow.on('ready-to-show', () => {
    mainWindow?.show();
    if (mainWindow) {
      initUpdateManager(mainWindow);
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  if (!app.isPackaged) {
    mainWindow.loadURL('http://localhost:5173');
    mainWindow.webContents.openDevTools();
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'));
  }
}

async function bootstrap() {
  Menu.setApplicationMenu(null);
  registerUpdateHandlers();

  try {
    if (app.isPackaged) {
      await startBackend();
    }
    createWindow();
  } catch (error) {
    const message = error instanceof Error ? error.message : '应用启动失败';
    dialog.showErrorBox(`${APP_PRODUCT_NAME} 启动失败`, message);
    app.quit();
  }
}

app.whenReady().then(bootstrap);

app.on('before-quit', (event) => {
  if (quitting) {
    return;
  }
  event.preventDefault();
  quitting = true;
  disposeUpdateManager();

  void (async () => {
    try {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('app:prepare-quit');
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
      await prepareAppQuit();
    } finally {
      app.exit(0);
    }
  })();
});

ipcMain.handle('app:prepareQuit', async () => {
  await prepareAppQuit();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

ipcMain.handle('app:getVersion', () => app.getVersion());
ipcMain.handle('app:getPath', (_event, name: string) => app.getPath(name as any));
ipcMain.handle('app:getBackendUrl', () => getApiBaseUrl(DEFAULT_BACKEND_PORT));
ipcMain.handle('app:isPackaged', () => app.isPackaged);
ipcMain.handle('system:getMetrics', () => collectLocalSystemMetrics());

ipcMain.handle('chat:hash-file', async (_event, filePath: string) => {
  const hash = createHash('sha256');
  await new Promise<void>((resolve, reject) => {
    const stream = createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', reject);
    stream.on('end', () => resolve());
  });
  return hash.digest('hex');
});

ipcMain.handle('chat:stat-file', async (_event, filePath: string) => {
  const stat = await fs.stat(filePath);
  return { size: stat.size, isFile: stat.isFile() };
});

ipcMain.handle(
  'notification:show',
  (_event, payload: { title: string; body: string }) => {
    try {
      const iconPath = app.isPackaged
        ? join(process.resourcesPath, 'icon.png')
        : join(__dirname, '../../frontend/public/icon.png');
      const icon = nativeImage.createFromPath(iconPath);
      const notification = new Notification({
        title: payload.title,
        body: payload.body,
        icon: icon.isEmpty() ? undefined : icon,
        silent: false,
      });
      notification.on('click', () => {
        if (!mainWindow) return;
        if (mainWindow.isMinimized()) mainWindow.restore();
        mainWindow.show();
        mainWindow.focus();
      });
      notification.show();
      return true;
    } catch {
      return false;
    }
  },
);

warmCpuSampler();
