export type AppUpdateStatus =
  | 'idle'
  | 'checking'
  | 'available'
  | 'not-available'
  | 'downloading'
  | 'downloaded'
  | 'error';

export type AppUpdateEventType =
  | 'checking'
  | 'available'
  | 'not-available'
  | 'progress'
  | 'downloaded'
  | 'error';

export interface AppUpdateProgress {
  percent: number;
  transferred: number;
  total: number;
  bytesPerSecond: number;
}

export interface AppUpdateEvent {
  type: AppUpdateEventType;
  version?: string;
  releaseNotes?: string | null;
  progress?: AppUpdateProgress;
  message?: string;
  manual?: boolean;
}

export interface AppUpdateState {
  status: AppUpdateStatus;
  currentVersion: string;
  remoteVersion: string | null;
  releaseNotes: string | null;
  progress: AppUpdateProgress | null;
  error: string | null;
  dismissedVersion: string | null;
  lastCheckedAt: string | null;
}

export interface AppUpdateCheckOptions {
  manual?: boolean;
}
