import os from 'os';
import { statfsSync } from 'fs';

export interface LocalSystemMetrics {
  cpu: number;
  memory: number;
  disk: number;
}

interface CpuTimes {
  idle: number;
  total: number;
}

let previousCpu: CpuTimes | null = null;

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

function sampleCpuTimes(): CpuTimes {
  const cpus = os.cpus();
  let idle = 0;
  let total = 0;
  for (const cpu of cpus) {
    const t = cpu.times;
    idle += t.idle;
    total += t.user + t.nice + t.sys + t.idle + t.irq;
  }
  return { idle, total };
}

/** 两次采样间隔内的系统 CPU 占用（%） */
export function readCpuPercent(): number {
  const current = sampleCpuTimes();
  if (!previousCpu) {
    previousCpu = current;
    return 0;
  }
  const idleDelta = current.idle - previousCpu.idle;
  const totalDelta = current.total - previousCpu.total;
  previousCpu = current;
  if (totalDelta <= 0) return 0;
  const usage = 100 - (100 * idleDelta) / totalDelta;
  return round1(Math.min(100, Math.max(0, usage)));
}

export function readMemoryPercent(): number {
  const total = os.totalmem();
  const free = os.freemem();
  if (total <= 0) return 0;
  return round1(((total - free) * 100) / total);
}

export function readDiskPercent(): number {
  try {
    const home = os.homedir();
    const stats = statfsSync(home);
    const total = stats.blocks * stats.bsize;
    const free = stats.bavail * stats.bsize;
    if (total <= 0) return 0;
    return round1(((total - free) * 100) / total);
  } catch {
    return 0;
  }
}

export function collectLocalSystemMetrics(): LocalSystemMetrics {
  return {
    cpu: readCpuPercent(),
    memory: readMemoryPercent(),
    disk: readDiskPercent(),
  };
}

/** 预热 CPU 采样，避免首次返回 0 */
export function warmCpuSampler(): void {
  readCpuPercent();
}
