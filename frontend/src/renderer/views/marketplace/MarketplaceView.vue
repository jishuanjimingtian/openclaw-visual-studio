<template>
  <div class="page marketplace-page">
    <div class="page-body">
    <n-tabs
      v-model:value="activeTab"
      type="line"
      animated
      class="marketplace-tabs"
      @update:value="onTabChange"
    >
      <n-tab-pane name="discover" tab="发现市场">
        <div class="marketplace-tab">
        <div class="marketplace-toolbar">
        <n-card size="small" class="toolbar-card">
          <n-space wrap :size="12">
            <n-input
              v-model:value="searchKeyword"
              placeholder="搜索 Skill（语义搜索 ClawHub + GitHub）..."
              clearable
              style="width: min(360px, 100%)"
              @keyup.enter="doMarketSearch"
            >
              <template #prefix>
                <n-icon><search-outline /></n-icon>
              </template>
            </n-input>
            <n-select
              v-model:value="marketSource"
              :options="sourceOptions"
              style="width: 130px"
            />
            <n-select
              v-model:value="marketSort"
              :options="sortOptions"
              style="width: 130px"
            />
            <n-button type="primary" :loading="store.marketLoading" @click="doMarketSearch">
              搜索
            </n-button>
            <n-button :loading="store.marketLoading" @click="loadHot">
              <template #icon><flame-outline /></template>
              热门
            </n-button>
            <n-select
              v-model:value="installScope"
              :options="installScopeOptions"
              style="width: 200px"
            />
            <n-switch v-model:value="showChinese" @update:value="onDisplayLocaleChange">
              <template #checked>中文显示</template>
              <template #unchecked>English</template>
            </n-switch>
            <n-switch v-model:value="store.marketUseLlm" @update:value="onLlmToggle">
              <template #checked>AI 润色</template>
              <template #unchecked>术语表</template>
            </n-switch>
            <n-switch v-model:value="store.localizeOnInstall">
              <template #checked>安装译 SKILL.md</template>
              <template #unchecked>保留英文 SKILL</template>
            </n-switch>
            <n-switch v-model:value="store.hideInstalledInMarket">
              <template #checked>隐藏已安装</template>
              <template #unchecked>显示已安装</template>
            </n-switch>
          </n-space>
          <n-text depth="3" style="font-size: 12px; margin-top: 8px; display: block">
            安装目标：工作区 skills 目录（新会话自动加载）；可选全局 ~/.openclaw/skills 供本机所有 Agent 共享。
            未配置工作区时将自动写入 openclaw.json（agents.defaults.workspace）。
            中文显示默认用术语表即时翻译；开启「AI 润色」需本机 openclaw.json 已配置 qwen apiKey。
          </n-text>
          <n-text v-if="store.marketFromCache" depth="3" style="font-size: 12px; margin-top: 8px; display: block">
            数据来自缓存（约 5 分钟刷新）
          </n-text>
        </n-card>
        </div>

        <div class="marketplace-content">
        <div v-if="store.marketLoading && store.marketSkills.length === 0" class="skill-grid">
          <n-card v-for="i in 8" :key="i">
            <n-skeleton text style="width: 55%" />
            <n-skeleton text :repeat="3" />
          </n-card>
        </div>

        <EmptyState
          v-else-if="!store.marketLoading && store.marketSkills.length === 0"
          description="未找到 Skill。可尝试更换关键词或切换来源。"
        />

        <div v-else class="skill-grid">
          <SkillMarketCard
            v-for="skill in filteredMarketSkills"
            :key="`${skill.source}-${skill.slug}`"
            :skill="skill"
            :installing="store.isInstalling(skill.slug)"
            :install-progress-text="installProgressText(skill.slug)"
            @install="installMarketSkill"
            @detail="viewMarketDetail"
          />
        </div>

        <n-space v-if="store.marketNextCursor || githubPage > 1" justify="center" class="load-more">
          <n-button
            v-if="store.marketNextCursor"
            :loading="store.marketLoading"
            @click="loadMoreClawHub"
          >
            加载更多 ClawHub
          </n-button>
          <n-button
            v-if="marketSource === 'all' || marketSource === 'GitHub'"
            :loading="store.marketLoading"
            @click="loadMoreGitHub"
          >
            更多 GitHub
          </n-button>
        </n-space>
        </div>
        </div>
      </n-tab-pane>

      <n-tab-pane name="installed" tab="已安装">
        <div class="marketplace-tab">
        <div class="marketplace-toolbar">
        <n-card size="small" class="toolbar-card">
          <n-space :size="12">
            <n-input
              v-model:value="installedSearch"
              placeholder="筛选已安装 Skill..."
              clearable
              style="width: 280px"
              @keyup.enter="searchInstalled"
            />
            <n-button :loading="store.loading" @click="refreshInstalled">
              从 OpenClaw 同步
            </n-button>
          </n-space>
        </n-card>
        </div>

        <div class="marketplace-content">
        <div v-if="store.loading && store.installedSkills.length === 0" class="skill-grid">
          <n-card v-for="i in 4" :key="i">
            <n-skeleton text :repeat="2" />
          </n-card>
        </div>

        <EmptyState
          v-else-if="!store.loading && store.installedSkills.length === 0"
          description="暂无已安装 Skill。在「发现市场」中浏览并安装。"
        />

        <div v-else class="skill-grid">
          <SkillMarketCard
            v-for="skill in installedCards"
            :key="skill.id"
            :skill="toMarketSkill(skill)"
            installed-view
            @uninstall="uninstallInstalled"
            @detail="viewInstalledDetail"
          />
        </div>

        <n-pagination
          v-if="store.totalPages > 1"
          class="pagination"
          :page="store.currentPage"
          :page-count="store.totalPages"
          @update:page="(p) => store.fetchInstalled(p)"
        />
        </div>
        </div>
      </n-tab-pane>
    </n-tabs>

    <n-modal v-model:show="detailVisible" style="width: 640px">
      <n-card :title="detailSkill?.name" :bordered="false" size="small">
        <div v-if="detailSkill" class="skill-detail">
          <n-space :size="8" style="margin-bottom: 12px">
            <n-tag size="small" :type="sourceType(detailSkill.source)">{{ detailSkill.source }}</n-tag>
            <n-tag v-if="detailSkill.slug" size="small">{{ detailSkill.slug }}</n-tag>
          </n-space>
          <div class="detail-row">
            <span class="label">描述</span>
            <span>{{ detailSkill.description || '—' }}</span>
          </div>
          <div
            v-if="detailSkill.localized && detailSkill.descriptionOriginal"
            class="detail-row"
          >
            <span class="label">原文</span>
            <n-text depth="3" style="font-size: 12px">{{ detailSkill.descriptionOriginal }}</n-text>
          </div>
          <div class="detail-row">
            <span class="label">作者</span>
            <span>{{ detailSkill.author || '—' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">版本</span>
            <span>{{ detailSkill.version }}</span>
          </div>
          <div class="detail-row">
            <span class="label">许可</span>
            <span>{{ detailSkill.license }}</span>
          </div>
          <div class="detail-row">
            <span class="label">热度</span>
            <n-space :size="8">
              <n-rate readonly :value="detailSkill.rating" size="small" allow-half />
              <span>{{ formatCount(detailSkill.stars || detailSkill.downloads) }} 星/下载</span>
            </n-space>
          </div>
          <div v-if="detailSkill.installPath" class="detail-row">
            <span class="label">OpenClaw</span>
            <n-text code style="font-size: 11px; word-break: break-all">{{ detailSkill.installPath }}</n-text>
          </div>
          <div v-if="detailSkill.homepageUrl" class="detail-row">
            <span class="label">链接</span>
            <n-button text tag="a" :href="detailSkill.homepageUrl" target="_blank" type="primary">
              在 {{ detailSkill.source }} 查看
            </n-button>
          </div>
        </div>
        <template #footer>
          <n-space justify="end">
            <n-button @click="detailVisible = false">关闭</n-button>
            <n-button
              v-if="detailSkill && canInstall(detailSkill)"
              type="primary"
              :loading="store.isInstalling(detailSkill.slug)"
              @click="installMarketSkill(detailSkill)"
            >
              {{ store.isInstalling(detailSkill.slug) ? '安装中…' : '安装' }}
            </n-button>
          </n-space>
        </template>
      </n-card>
    </n-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useMessage } from 'naive-ui';
import {
  NInput, NButton, NTag, NSpace, NCard, NModal, NSelect, NRate, NSkeleton,
  NPagination, NIcon, NTabs, NTabPane, NText, NSwitch,
} from 'naive-ui';
import { SearchOutline, FlameOutline } from '@vicons/ionicons5';
import EmptyState from '@/components/EmptyState.vue';
import SkillMarketCard from './components/SkillMarketCard.vue';
import { useSkillStore } from '@/stores/skill';
import type { MarketSkill, Skill, SkillInstallScope } from '@shared/types';

const message = useMessage();
const store = useSkillStore();

const activeTab = ref<'discover' | 'installed'>('discover');
const searchKeyword = ref('');
const installedSearch = ref('');
const marketSource = ref('all');
const marketSort = ref('downloads');
const githubPage = ref(1);

const detailVisible = ref(false);
const detailSkill = ref<MarketSkill | null>(null);
const installProgressMap = ref<Record<string, string>>({});
const installScope = ref<SkillInstallScope>('workspace');
const showChinese = ref(true);

const installScopeOptions = [
  { label: '工作区（推荐）', value: 'workspace' },
  { label: '全局共享', value: 'global' },
  { label: '工作区 + 全局', value: 'both' },
];

const sourceOptions = [
  { label: '全部来源', value: 'all' },
  { label: 'ClawdHub', value: 'ClawdHub' },
  { label: 'GitHub', value: 'GitHub' },
];

const sortOptions = [
  { label: '下载最多', value: 'downloads' },
  { label: '星级最高', value: 'stars' },
  { label: '最近更新', value: 'updated' },
  { label: '最新发布', value: 'newest' },
];

const sourceTypeMap = { ClawdHub: 'success', GitHub: 'info' } as const;

const filteredMarketSkills = computed(() => {
  let list = store.marketSkills;
  if (store.hideInstalledInMarket) {
    list = list.filter((s) => s.status !== 'installed' && s.status !== 'update_available');
  }
  const q = searchKeyword.value.trim().toLowerCase();
  if (!q || store.marketSort === 'search') {
    return list;
  }
  return list.filter(
    (s) =>
      s.name.toLowerCase().includes(q)
      || s.slug.toLowerCase().includes(q)
      || (s.description?.toLowerCase().includes(q) ?? false)
      || (s.nameOriginal?.toLowerCase().includes(q) ?? false)
      || (s.descriptionOriginal?.toLowerCase().includes(q) ?? false),
  );
});

async function reloadMarketList() {
  if (searchKeyword.value.trim()) {
    await store.searchMarket(searchKeyword.value, marketSource.value, 1);
  } else if (marketSort.value === 'downloads' && marketSource.value === 'all') {
    await loadHot();
  } else {
    await store.browseMarket(marketSort.value, marketSource.value, 1);
  }
}

function onDisplayLocaleChange(zh: boolean) {
  store.marketLocale = zh ? 'zh' : 'en';
  reloadMarketList();
}

function onLlmToggle() {
  reloadMarketList();
}

const installedCards = computed(() => {
  const q = installedSearch.value.trim().toLowerCase();
  let list = store.installedSkills.filter((s) => s.status === 'installed');
  if (q) {
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(q)
        || (s.description?.toLowerCase().includes(q) ?? false),
    );
  }
  return list;
});

function sourceType(source: 'ClawdHub' | 'GitHub') {
  return sourceTypeMap[source] || 'default';
}

function formatCount(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}

function toMarketSkill(skill: Skill): MarketSkill {
  return {
    slug: skill.marketSlug || skill.id,
    name: skill.name,
    version: skill.version,
    author: skill.author,
    description: skill.description,
    source: skill.source,
    status: skill.status,
    rating: skill.rating,
    downloads: skill.downloads,
    stars: skill.downloads,
    license: skill.license,
    installedId: skill.id,
    installPath: skill.installPath,
  };
}

function installProgressText(slug: string) {
  return installProgressMap.value[slug] || '正在下载并安装到 OpenClaw…';
}

function startInstallProgress(slug: string) {
  installProgressMap.value = {
    ...installProgressMap.value,
    [slug]: '正在下载 Skill 包…',
  };
  window.setTimeout(() => {
    if (store.isInstalling(slug)) {
      installProgressMap.value = { ...installProgressMap.value, [slug]: '正在写入 OpenClaw skills 目录…' };
    }
  }, 1200);
  window.setTimeout(() => {
    if (store.isInstalling(slug)) {
      installProgressMap.value = { ...installProgressMap.value, [slug]: '正在完成安装…' };
    }
  }, 2800);
}

function clearInstallProgress(slug: string) {
  const next = { ...installProgressMap.value };
  delete next[slug];
  installProgressMap.value = next;
}

function canInstall(skill: MarketSkill) {
  return skill.status === 'not_installed' || skill.status === 'available';
}

onMounted(() => {
  loadHot();
  store.fetchInstalled(1);
});

function onTabChange(tab: string) {
  if (tab === 'installed') {
    store.fetchInstalled(store.currentPage);
  }
}

async function refreshInstalled() {
  await store.fetchInstalled(store.currentPage);
  message.success('已从 OpenClaw 同步已安装 Skill');
}

async function loadHot() {
  marketSort.value = 'downloads';
  await store.fetchTrending(24);
}

async function doMarketSearch() {
  githubPage.value = 1;
  if (searchKeyword.value.trim()) {
    await store.searchMarket(searchKeyword.value, marketSource.value, 1);
  } else if (marketSort.value === 'downloads' && marketSource.value === 'all') {
    await loadHot();
  } else {
    await store.browseMarket(marketSort.value, marketSource.value, 1);
  }
}

async function loadMoreClawHub() {
  if (!store.marketNextCursor) return;
  await store.browseMarket(
    marketSort.value,
    marketSource.value === 'all' ? 'ClawdHub' : marketSource.value,
    1,
    store.marketNextCursor,
  );
}

async function loadMoreGitHub() {
  githubPage.value += 1;
  await store.browseMarket(marketSort.value, 'GitHub', githubPage.value);
}

function searchInstalled() {
  if (installedSearch.value.trim()) {
    store.searchInstalled(installedSearch.value);
  } else {
    store.fetchInstalled();
  }
}

async function installMarketSkill(skill: MarketSkill) {
  startInstallProgress(skill.slug);
  try {
    const result = await store.installFromMarket(skill, installScope.value);
    const pathHint = result.installPath ? `\n${result.installPath}` : '';
    if (result.openclawReady) {
      message.success(`${result.message}${pathHint}`);
    } else {
      message.warning(`${result.message}${pathHint}`);
    }
    detailVisible.value = false;
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '安装失败');
  } finally {
    clearInstallProgress(skill.slug);
  }
}

async function uninstallInstalled(skill: MarketSkill) {
  if (!skill.installedId) return;
  try {
    await store.uninstallSkill(skill.installedId);
    message.success('已卸载');
  } catch {
    message.error('卸载失败');
  }
}

function viewMarketDetail(skill: MarketSkill) {
  detailSkill.value = skill;
  detailVisible.value = true;
}

function viewInstalledDetail(skill: MarketSkill) {
  viewMarketDetail(skill);
}
</script>

<style scoped>
.marketplace-page .page-body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.marketplace-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.marketplace-tabs :deep(.n-tabs-nav) {
  flex-shrink: 0;
}

.marketplace-tabs :deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.marketplace-tabs :deep(.n-tab-pane) {
  height: 100%;
  overflow: hidden;
}

.marketplace-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.marketplace-toolbar {
  flex-shrink: 0;
  padding: 0 0 12px;
}

.marketplace-content {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}

.toolbar-card {
  margin-bottom: 0;
}
.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.load-more {
  margin-top: 20px;
}
.pagination {
  margin-top: 16px;
  justify-content: center;
}
.skill-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.detail-row .label {
  font-weight: 500;
  min-width: 48px;
  color: var(--n-text-color-2);
  flex-shrink: 0;
}
</style>