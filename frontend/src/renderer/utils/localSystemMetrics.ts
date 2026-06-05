import type { LocalSystemMetrics } from '@shared/types';

export function hasLocalSystemMetrics(): boolean {
  return typeof window !== 'undefined' && typeof window.electronAPI?.getSystemMetrics === 'function';
}

export async function fetchLocalSystemMetrics(): Promise<LocalSystemMetrics | null> {
  const getMetrics = window.electronAPI?.getSystemMetrics;
  if (!getMetrics) return null;
  try {
    return await getMetrics();
  } catch {
    return null;
  }
}
