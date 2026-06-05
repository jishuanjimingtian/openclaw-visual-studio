import type { DashboardStats, SystemMetrics } from '@shared/types';

function isFiniteNumber(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v);
}

export function isValidStats(data: unknown): data is DashboardStats {
  if (!data || typeof data !== 'object') return false;
  const s = data as DashboardStats;
  return (
    isFiniteNumber(s.totalConversations) &&
    isFiniteNumber(s.activeModels) &&
    isFiniteNumber(s.totalModels) &&
    isFiniteNumber(s.activeDeployments) &&
    isFiniteNumber(s.totalDeployments) &&
    isFiniteNumber(s.installedSkills) &&
    isFiniteNumber(s.totalSkills) &&
    isFiniteNumber(s.totalMessages) &&
    isFiniteNumber(s.messagesToday)
  );
}

/** 避免后端偶发返回全 0 覆盖已有有效统计 */
export function mergeDashboardStats(prev: DashboardStats, next: DashboardStats): DashboardStats {
  if (!isValidStats(next)) return prev;

  // 仅当「会话与消息」均为 0、但其它指标有值时，视为 Gateway 未合并的异常快照，不覆盖
  const nextLooksEmpty =
    next.totalConversations === 0 &&
    next.totalMessages === 0 &&
    next.messagesToday === 0 &&
    (next.totalModels > 0 || next.installedSkills > 0 || next.totalSkills > 0);

  const prevHadData =
    prev.totalConversations > 0 ||
    prev.totalModels > 0 ||
    prev.installedSkills > 0 ||
    prev.totalMessages > 0 ||
    prev.totalSkills > 0;

  if (nextLooksEmpty && prevHadData) return prev;
  return next;
}

export function mergeResourcePercent(prev: number, next: number): number {
  if (!isFiniteNumber(next) || next < 0) return prev;
  if (next > 0) return next;
  return prev;
}

/** 今日 Token：采用最新一次有效拉取（允许向下修正误报的累计值） */
export function mergeTokenUsage(prev: number, next: number): number {
  if (!isFiniteNumber(next) || next < 0) return prev;
  if (next === 0) return prev;
  return next;
}

export function mergeBackendMetrics(
  prev: SystemMetrics,
  next: SystemMetrics,
  options: { useBackendResources: boolean },
): SystemMetrics {
  const cpu = options.useBackendResources
    ? mergeResourcePercent(prev.cpu, next.cpu)
    : prev.cpu;
  const memory = options.useBackendResources
    ? mergeResourcePercent(prev.memory, next.memory)
    : prev.memory;
  const disk = options.useBackendResources
    ? mergeResourcePercent(prev.disk, next.disk)
    : prev.disk;

  return {
    cpu,
    memory,
    disk,
    uptime: isFiniteNumber(next.uptime) && next.uptime > 0 ? next.uptime : prev.uptime,
    sessionCount: isFiniteNumber(next.sessionCount)
      ? Math.max(prev.sessionCount, next.sessionCount)
      : prev.sessionCount,
    tokenUsage: mergeTokenUsage(prev.tokenUsage, next.tokenUsage),
  };
}
