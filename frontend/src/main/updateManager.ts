import { app, BrowserWindow, ipcMain } from 'electron';
import { autoUpdater } from 'electron-updater';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { join } from 'path';
import type {
  AppUpdateCheckOptions,
  AppUpdateEvent,
  AppUpdateState,
} from '@shared/update';
import {
  formatUpdateError,
  resolveUpdateFeeds,
  type GenericUpdateFeed,
} from './updateFeed';

const AUTO_CHECK_DELAY_MS = 4000;
const CHECK_TIMEOUT_MS = 30_000;
const UPDATE_CHANNEL = 'update:event';

interface UpdatePrefs {
  dismissedVersion: string | null;
}

let mainWindow: BrowserWindow | null = null;
let initialized = false;
let manualCheck = false;
let pendingVersion: string | null = null;
let suppressErrorEmit = false;
let updateFeeds: GenericUpdateFeed[] = [];

const state: AppUpdateState = {
  status: 'idle',
  currentVersion: app.getVersion(),
  remoteVersion: null,
  releaseNotes: null,
  progress: null,
  error: null,
  dismissedVersion: null,
  lastCheckedAt: null,
};

function prefsPath(): string {
  return join(app.getPath('userData'), 'update-prefs.json');
}

function loadPrefs(): UpdatePrefs {
  try {
    const raw = readFileSync(prefsPath(), 'utf8');
    const parsed = JSON.parse(raw) as Partial<UpdatePrefs>;
    return {
      dismissedVersion:
        typeof parsed.dismissedVersion === 'string' ? parsed.dismissedVersion : null,
    };
  } catch {
    return { dismissedVersion: null };
  }
}

function savePrefs(prefs: UpdatePrefs): void {
  const dir = app.getPath('userData');
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }
  writeFileSync(prefsPath(), JSON.stringify(prefs, null, 2), 'utf8');
  state.dismissedVersion = prefs.dismissedVersion;
}

function emit(event: AppUpdateEvent): void {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  mainWindow.webContents.send(UPDATE_CHANNEL, event);
}

function setStatus(next: AppUpdateState['status']): void {
  state.status = next;
}

function formatReleaseNotes(notes: unknown): string | null {
  if (notes == null) return null;
  if (typeof notes === 'string') return notes.trim() || null;
  if (Array.isArray(notes)) {
    return notes
      .map((item) => (typeof item === 'string' ? item : ''))
      .filter(Boolean)
      .join('\n');
  }
  return null;
}

function shouldPromptAuto(version: string): boolean {
  return version !== state.dismissedVersion;
}

function notifyAvailable(version: string, releaseNotes: string | null, manual: boolean): void {
  state.remoteVersion = version;
  state.releaseNotes = releaseNotes;
  setStatus('available');
  emit({
    type: 'available',
    version,
    releaseNotes,
    manual,
  });
}

function isNotFoundError(err: unknown): boolean {
  const raw = err instanceof Error ? err.message : String(err);
  return /404|not found|cannot find|ENOENT/i.test(raw);
}

function applyFeed(feed: GenericUpdateFeed): void {
  autoUpdater.setFeedURL(feed);
}

function checkOnce(): Promise<void> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      cleanup();
      reject(new Error('检查更新超时'));
    }, CHECK_TIMEOUT_MS);

    const onError = (err: Error) => {
      cleanup();
      reject(err);
    };
    const onNotAvailable = () => {
      cleanup();
      resolve();
    };
    const onAvailable = () => {
      cleanup();
      resolve();
    };
    const cleanup = () => {
      clearTimeout(timeout);
      autoUpdater.off('error', onError);
      autoUpdater.off('update-not-available', onNotAvailable);
      autoUpdater.off('update-available', onAvailable);
    };

    autoUpdater.once('error', onError);
    autoUpdater.once('update-not-available', onNotAvailable);
    autoUpdater.once('update-available', onAvailable);

    void autoUpdater.checkForUpdates();
  });
}

function wireAutoUpdater(): void {
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = false;

  autoUpdater.on('checking-for-update', () => {
    state.error = null;
    setStatus('checking');
    emit({ type: 'checking', manual: manualCheck });
  });

  autoUpdater.on('update-available', (info) => {
    const version = info.version;
    const releaseNotes = formatReleaseNotes(info.releaseNotes);
    pendingVersion = version;
    state.lastCheckedAt = new Date().toISOString();

    if (manualCheck || shouldPromptAuto(version)) {
      notifyAvailable(version, releaseNotes, manualCheck);
    } else {
      setStatus('idle');
    }
  });

  autoUpdater.on('update-not-available', () => {
    state.remoteVersion = null;
    state.releaseNotes = null;
    pendingVersion = null;
    state.lastCheckedAt = new Date().toISOString();
    setStatus('not-available');
    emit({ type: 'not-available', manual: manualCheck });
  });

  autoUpdater.on('download-progress', (progress) => {
    const snapshot = {
      percent: progress.percent,
      transferred: progress.transferred,
      total: progress.total,
      bytesPerSecond: progress.bytesPerSecond,
    };
    state.progress = snapshot;
    setStatus('downloading');
    emit({
      type: 'progress',
      version: state.remoteVersion ?? undefined,
      progress: snapshot,
    });
  });

  autoUpdater.on('update-downloaded', (info) => {
    state.remoteVersion = info.version;
    state.progress = null;
    setStatus('downloaded');
    emit({
      type: 'downloaded',
      version: info.version,
      releaseNotes: formatReleaseNotes(info.releaseNotes),
    });
  });

  autoUpdater.on('error', (err) => {
    const message = formatUpdateError(err);
    state.error = message;
    setStatus('error');
    if (!suppressErrorEmit) {
      emit({ type: 'error', message, manual: manualCheck });
    }
  });
}

async function runCheck(options?: AppUpdateCheckOptions): Promise<AppUpdateState> {
  if (!app.isPackaged) {
    return { ...state };
  }

  manualCheck = Boolean(options?.manual);
  state.error = null;

  const feeds = updateFeeds.length > 0
    ? updateFeeds
    : resolveUpdateFeeds(process.resourcesPath);

  let lastError: unknown = null;

  const attempts = feeds.length > 0
    ? feeds
    : [null];

  for (let index = 0; index < attempts.length; index += 1) {
    const feed = attempts[index];
    suppressErrorEmit = index < attempts.length - 1;

    if (feed) {
      applyFeed(feed);
    }

    try {
      await checkOnce();
      suppressErrorEmit = false;
      return { ...state };
    } catch (err) {
      lastError = err;
      if (isNotFoundError(err)) {
        break;
      }
    }
  }

  suppressErrorEmit = false;
  const message = formatUpdateError(lastError ?? state.error ?? '检查更新失败');
  state.error = message;
  setStatus('error');
  emit({ type: 'error', message, manual: manualCheck });
  return { ...state };
}

let ipcRegistered = false;

function registerIpc(): void {
  if (ipcRegistered) return;
  ipcRegistered = true;

  ipcMain.handle('update:getState', () => ({ ...state }));
  ipcMain.handle('update:check', (_event, options?: AppUpdateCheckOptions) =>
    runCheck(options),
  );
  ipcMain.handle('update:download', async () => {
    if (!app.isPackaged) return { ...state };
    setStatus('downloading');
    try {
      await autoUpdater.downloadUpdate();
    } catch (err) {
      const message = formatUpdateError(err);
      state.error = message;
      setStatus('error');
      emit({ type: 'error', message, manual: manualCheck });
    }
    return { ...state };
  });
  ipcMain.handle('update:dismiss', () => {
    const version = pendingVersion ?? state.remoteVersion;
    if (version) {
      savePrefs({ dismissedVersion: version });
    }
    setStatus('idle');
    return { ...state };
  });
  ipcMain.handle('update:quitAndInstall', () => {
    if (!app.isPackaged) return;
    autoUpdater.quitAndInstall(false, true);
  });
}

export function registerUpdateHandlers(): void {
  registerIpc();
  state.currentVersion = app.getVersion();
  if (app.isPackaged) {
    const prefs = loadPrefs();
    state.dismissedVersion = prefs.dismissedVersion;
    updateFeeds = resolveUpdateFeeds(process.resourcesPath);
    if (updateFeeds[0]) {
      applyFeed(updateFeeds[0]);
    }
  }
}

export function initUpdateManager(win: BrowserWindow): void {
  mainWindow = win;
  if (!app.isPackaged || initialized) {
    return;
  }

  initialized = true;
  wireAutoUpdater();

  setTimeout(() => {
    void runCheck({ manual: false });
  }, AUTO_CHECK_DELAY_MS);
}

export function disposeUpdateManager(): void {
  mainWindow = null;
}
