import type { Component } from 'vue';
import {
  GridOutline, ChatbubblesOutline, ChatboxOutline, GitBranchOutline,
  AnalyticsOutline, SettingsOutline, HardwareChipOutline,
  StorefrontOutline, CloudDownloadOutline, PulseOutline,
  CogOutline, LibraryOutline, TimerOutline,
} from '@vicons/ionicons5';

const iconMap: Record<string, Component> = {
  'grid-outline': GridOutline,
  'chatbox-outline': ChatboxOutline,
  'chatbubbles-outline': ChatbubblesOutline,
  'git-branch-outline': GitBranchOutline,
  'analytics-outline': AnalyticsOutline,
  'settings-outline': SettingsOutline,
  'hardware-chip-outline': HardwareChipOutline,
  'storefront-outline': StorefrontOutline,
  'cloud-download-outline': CloudDownloadOutline,
  'pulse-outline': PulseOutline,
  'cog-outline': CogOutline,
  'library-outline': LibraryOutline,
  'timer-outline': TimerOutline,
};

export function getRouteIcon(iconName?: string): Component | undefined {
  if (!iconName) return undefined;
  return iconMap[iconName];
}
