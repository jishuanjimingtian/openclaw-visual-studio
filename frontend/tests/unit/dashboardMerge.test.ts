import { describe, expect, it } from 'vitest';
import {
  mergeDashboardStats,
  mergeResourcePercent,
  mergeTokenUsage,
  isValidStats,
} from '@/utils/dashboardMerge';
import type { DashboardStats } from '@shared/types';

const baseStats: DashboardStats = {
  totalConversations: 5,
  activeModels: 2,
  totalModels: 4,
  activeDeployments: 0,
  totalDeployments: 1,
  installedSkills: 3,
  totalSkills: 10,
  totalMessages: 100,
  messagesToday: 12,
};

describe('dashboardMerge', () => {
  it('rejects invalid stats', () => {
    expect(isValidStats(null)).toBe(false);
    expect(isValidStats({ totalConversations: NaN })).toBe(false);
  });

  it('keeps previous stats when backend returns sessions zero but other metrics present', () => {
    const partial: DashboardStats = {
      ...baseStats,
      totalConversations: 0,
      totalMessages: 0,
      messagesToday: 0,
      totalModels: 4,
      installedSkills: 4,
    };
    expect(mergeDashboardStats(baseStats, partial)).toEqual(baseStats);
  });

  it('accepts legitimate zero from empty workspace', () => {
    const empty: DashboardStats = {
      totalConversations: 0,
      activeModels: 0,
      totalModels: 0,
      activeDeployments: 0,
      totalDeployments: 0,
      installedSkills: 0,
      totalSkills: 0,
      totalMessages: 0,
      messagesToday: 0,
    };
    expect(mergeDashboardStats(empty, empty)).toEqual(empty);
  });

  it('uses latest non-zero token count and ignores zero refresh', () => {
    expect(mergeTokenUsage(500, 0)).toBe(500);
    expect(mergeTokenUsage(6_000_000, 120_000)).toBe(120_000);
    expect(mergeTokenUsage(100, 250)).toBe(250);
  });

  it('keeps previous resource percent when new read is zero', () => {
    expect(mergeResourcePercent(42, 0)).toBe(42);
    expect(mergeResourcePercent(42, 55)).toBe(55);
  });
});
