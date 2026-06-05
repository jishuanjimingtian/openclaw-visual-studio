import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { skillApi } from '@/api/skill';
import type {
  Skill, MarketSkill, PageRequest, SkillInstallScope, SkillInstallRequest,
} from '@shared/types';

export const useSkillStore = defineStore('skill', () => {
  const installedSkills = ref<Skill[]>([]);
  const marketSkills = ref<MarketSkill[]>([]);
  const currentSkill = ref<Skill | null>(null);
  const loading = ref(false);
  const marketLoading = ref(false);
  const searchKeyword = ref('');
  const totalElements = ref(0);
  const totalPages = ref(0);
  const currentPage = ref(1);
  const marketNextCursor = ref<string | null>(null);
  const marketFromCache = ref(false);
  const marketSort = ref('downloads');
  const marketSource = ref('all');
  const marketLocale = ref('zh');
  const marketUseLlm = ref(false);
  const localizeOnInstall = ref(true);
  const hideInstalledInMarket = ref(false);
  const installingSlugs = ref<Set<string>>(new Set());

  const installedOnly = computed(() =>
    installedSkills.value.filter((s: Skill) => s.status === 'installed'),
  );

  function isInstalling(slug: string) {
    return installingSlugs.value.has(slug);
  }

  function setInstalling(slug: string, active: boolean) {
    const next = new Set(installingSlugs.value);
    if (active) next.add(slug);
    else next.delete(slug);
    installingSlugs.value = next;
  }

  async function fetchInstalled(page = 1, size = 20) {
    loading.value = true;
    try {
      const params: PageRequest = { page, pageSize: size, sortBy: 'installedAt', sortOrder: 'desc' };
      const res = await skillApi.listSkills(params);
      installedSkills.value = res.data.content;
      totalElements.value = res.data.totalElements;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.number + 1;
    } finally {
      loading.value = false;
    }
  }

  async function fetchTrending(limit = 24) {
    marketLoading.value = true;
    try {
      const res = await skillApi.getTrending(limit, marketLocale.value, marketUseLlm.value);
      marketSkills.value = res.data.items;
      marketNextCursor.value = res.data.nextCursor ?? null;
      marketFromCache.value = res.data.fromCache ?? false;
      marketSort.value = res.data.sort ?? 'downloads';
      marketSource.value = res.data.source ?? 'all';
    } finally {
      marketLoading.value = false;
    }
  }

  async function browseMarket(sort = 'downloads', source = 'all', page = 1, cursor?: string) {
    marketLoading.value = true;
    marketSort.value = sort;
    marketSource.value = source;
    try {
      const res = await skillApi.browseMarket({
        sort, source, limit: 24, page, cursor,
        locale: marketLocale.value, llm: marketUseLlm.value,
      });
      marketSkills.value = res.data.items;
      marketNextCursor.value = res.data.nextCursor ?? null;
      marketFromCache.value = res.data.fromCache ?? false;
    } finally {
      marketLoading.value = false;
    }
  }

  async function searchMarket(keyword: string, source = 'all', page = 1) {
    marketLoading.value = true;
    searchKeyword.value = keyword;
    marketSource.value = source;
    try {
      if (!keyword.trim()) {
        await fetchTrending();
        return;
      }
      const res = await skillApi.searchMarket(keyword.trim(), {
        source, limit: 24, page,
        locale: marketLocale.value, llm: marketUseLlm.value,
      });
      marketSkills.value = res.data.items;
      marketNextCursor.value = res.data.nextCursor ?? null;
      marketFromCache.value = res.data.fromCache ?? false;
      marketSort.value = 'search';
    } finally {
      marketLoading.value = false;
    }
  }

  async function searchInstalled(keyword: string, page = 1, size = 20) {
    loading.value = true;
    searchKeyword.value = keyword;
    try {
      const params: PageRequest = { page, pageSize: size };
      const res = await skillApi.searchInstalled(keyword, params);
      installedSkills.value = res.data.content;
      totalElements.value = res.data.totalElements;
      totalPages.value = res.data.totalPages;
      currentPage.value = res.data.number + 1;
    } finally {
      loading.value = false;
    }
  }

  async function installFromMarket(item: MarketSkill, scope: SkillInstallScope = 'workspace') {
    setInstalling(item.slug, true);
    try {
      const body: SkillInstallRequest = {
        slug: item.slug,
        source: item.source,
        version: item.version,
        scope,
        name: item.name,
        author: item.author,
        description: item.description,
        license: item.license,
        rating: item.rating,
        downloads: item.downloads,
        githubRepo: item.githubRepo,
        localizeToChinese: localizeOnInstall.value,
      };
      const res = await skillApi.installFromMarket(body);
      const idx = marketSkills.value.findIndex((m) => m.slug === item.slug && m.source === item.source);
      if (idx !== -1) {
        marketSkills.value[idx] = {
          ...marketSkills.value[idx],
          status: 'installed',
          installedId: res.data.skillId,
          installPath: res.data.installPath,
        };
      }
      await fetchInstalled(1);
      return res.data;
    } finally {
      setInstalling(item.slug, false);
    }
  }

  async function installSkill(skill: Partial<Skill>) {
    const res = await skillApi.installSkill(skill);
    installedSkills.value.unshift(res.data);
    return res.data;
  }

  async function updateSkill(id: string, skill: Partial<Skill>) {
    const res = await skillApi.updateSkill(id, skill);
    const idx = installedSkills.value.findIndex((s: Skill) => s.id === id);
    if (idx !== -1) installedSkills.value[idx] = res.data;
    if (currentSkill.value?.id === id) currentSkill.value = res.data;
    return res.data;
  }

  async function uninstallSkill(id: string) {
    await skillApi.uninstallSkill(id);
    const idx = installedSkills.value.findIndex((s: Skill) => s.id === id);
    if (idx !== -1) installedSkills.value[idx] = { ...installedSkills.value[idx], status: 'not_installed' };
    for (const m of marketSkills.value) {
      if (m.installedId === id) {
        m.status = 'not_installed';
        m.installedId = undefined;
      }
    }
  }

  return {
    skills: marketSkills,
    installedSkills,
    marketSkills,
    currentSkill,
    loading,
    marketLoading,
    searchKeyword,
    totalElements,
    totalPages,
    currentPage,
    marketNextCursor,
    marketFromCache,
    marketSort,
    marketSource,
    marketLocale,
    marketUseLlm,
    localizeOnInstall,
    hideInstalledInMarket,
    installingSlugs,
    isInstalling,
    installedOnly,
    fetchSkills: fetchInstalled,
    fetchInstalled,
    fetchTrending,
    browseMarket,
    searchMarket,
    searchSkills: searchInstalled,
    searchInstalled,
    installFromMarket,
    installSkill,
    updateSkill,
    uninstallSkill,
  };
});
