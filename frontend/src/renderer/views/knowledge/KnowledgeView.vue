<template>
  <div class="page knowledge-page">
    <HeaderToolbar>
      <n-button size="small" quaternary :loading="store.loading" @click="refreshAll">刷新</n-button>
      <n-button size="small" quaternary :loading="store.loading" @click="onAutoDiscover(false)">定位</n-button>
      <n-button size="small" quaternary :loading="store.indexRebuilding" @click="store.rebuildIndex()">索引</n-button>
    </HeaderToolbar>

    <div class="kb-status-bar">
      <span class="dot" :class="gatewayOnline ? 'on' : 'off'" />
      <span><b>{{ store.overview?.fileCount ?? 0 }}</b> 文件</span>
      <span v-if="statusDetail" class="sep">·</span>
      <span v-if="statusDetail">{{ statusDetail }}</span>
      <span class="sep">·</span>
      <span>{{ formatBytes(store.overview?.memoryMdSizeBytes ?? 0) }}</span>
      <span class="sep">·</span>
      <span :class="gatewayOnline ? 'ok' : 'warn'">GW {{ gatewayOnline ? '已连' : '断' }}</span>
      <span class="sep">·</span>
      <span class="ok">{{ store.overview?.indexStatusSummary ?? '—' }}</span>
      <n-tooltip>
        <template #trigger><code>{{ shortWorkspace }}</code></template>
        {{ store.overview?.workspacePath ?? '—' }}
      </n-tooltip>
    </div>

    <n-alert v-if="store.loadError" class="kb-alert" type="error" :title="store.loadError" closable>
      请确认后端已启动（端口 8089）。
    </n-alert>

    <n-alert
      v-else-if="store.overview?.fileCount === 0 && hasMemoryElsewhere"
      class="kb-alert"
      type="warning"
      title="当前工作区无记忆文件"
      closable
    />

    <n-collapse v-if="showWorkspacePicker" class="kb-workspace-pick">
      <n-collapse-item title="切换工作区" name="ws">
        <n-space :size="6" wrap>
          <n-button
            v-for="c in store.workspaceCandidates.filter((x) => x.memoryFileCount > 0)"
            :key="c.path"
            size="tiny"
            :type="c.active ? 'primary' : 'default'"
            @click="pickWorkspace(c.path)"
          >
            {{ workspaceLabel(c) }} · {{ c.memoryFileCount }}
          </n-button>
        </n-space>
      </n-collapse-item>
    </n-collapse>

    <div class="kb-shell">
      <!-- 左：文件树 -->
      <aside class="kb-sidebar">
        <div class="kb-sidebar-head">记忆文件</div>
        <div class="kb-sidebar-tree">
          <KnowledgeFileTree
            :files="sidebarFiles"
            :selected-path="store.selectedPath"
            @select="onFileSelect"
          />
        </div>
        <div class="kb-sidebar-foot">
          <n-button block size="small" @click="createMemory">新建 MEMORY.md</n-button>
          <n-button block size="small" secondary @click="createTodayDiary">今日日记</n-button>
          <n-button
            v-for="item in missingConfigFiles"
            :key="item.path"
            block
            size="small"
            quaternary
            @click="createWorkspaceFile(item)"
          >
            新建 {{ item.label }}.md
          </n-button>
        </div>
      </aside>

      <!-- 中：星云 / 编辑 -->
      <main class="kb-main">
        <div class="kb-toolbar">
          <n-radio-group v-model:value="centerView" size="small" class="view-toggle">
            <n-radio-button value="graph">星云图</n-radio-button>
            <n-radio-button value="edit">编辑</n-radio-button>
          </n-radio-group>
          <template v-if="centerView === 'graph'">
            <n-input
              v-model:value="store.searchQuery"
              size="small"
              class="search-input"
              placeholder="搜索记忆…"
              clearable
              @keyup.enter="onSearch"
              @clear="store.clearSearchOverlay()"
            >
              <template #prefix><n-icon size="14"><search-outline /></n-icon></template>
            </n-input>
            <n-button size="small" type="primary" :loading="store.searching" @click="onSearch">搜索</n-button>
            <n-switch
              v-model:value="store.includeChunks"
              size="small"
              @update:value="onChunksToggle"
            >
              <template #checked>展开条目</template>
              <template #unchecked>仅文件</template>
            </n-switch>
          </template>
          <template v-else>
            <n-tag v-if="store.selectedPath" size="small" :bordered="false">{{ store.selectedPath }}</n-tag>
            <n-tag v-if="store.editorDirty" size="small" type="warning" :bordered="false">未保存</n-tag>
          </template>
          <div class="kb-toolbar-spacer" />
          <template v-if="centerView === 'edit'">
            <n-button
              size="small"
              type="primary"
              :loading="store.saving"
              :disabled="!store.selectedPath || !store.editorDirty"
              @click="saveCurrent"
            >
              保存
            </n-button>
            <n-button size="small" type="error" secondary :disabled="!store.selectedPath" @click="confirmDelete">
              删除
            </n-button>
          </template>
          <n-button
            v-if="centerView === 'graph' && showInspector"
            size="small"
            quaternary
            @click="inspectorOpen = false"
          >
            隐藏侧栏
          </n-button>
          <n-button
            v-else-if="centerView === 'graph' && !showInspector"
            size="small"
            quaternary
            @click="inspectorOpen = true"
          >
            显示预览
          </n-button>
        </div>

        <div class="kb-body">
          <n-spin :show="store.loading && centerView === 'graph'">
            <KnowledgeGraphCanvas
              v-if="centerView === 'graph'"
              :graph="store.displayGraph"
              :highlight-node-ids="store.highlightNodeIds"
              :focus-node-id="store.selectedNodeId"
              @node-click="onGraphNodeClick"
              @seed="createMemory"
            />
          </n-spin>
          <div v-if="centerView === 'edit'" class="kb-editor-wrap">
            <KnowledgeEditor
              v-if="store.selectedPath"
              :content="store.editorContent"
              @update:content="(v) => (store.editorContent = v)"
              @dirty="(d) => (store.editorDirty = d)"
            />
            <n-empty v-else description="从左侧选择文件，或新建记忆" />
          </div>
        </div>

        <div v-if="store.searchResult?.hits.length && centerView === 'graph'" class="kb-search-panel">
          <KnowledgeSearchResults
            :hits="store.searchResult.hits"
            :fallback="store.searchResult.fallback"
            @select="onSearchHit"
          />
        </div>
      </main>

      <!-- 右：预览 -->
      <KnowledgeInspector
        v-if="centerView === 'graph' && showInspector"
        :node="store.selectedNode"
        :path="store.selectedPath"
        :content="previewContent"
        :snippet="selectedHitSnippet"
        @close="clearInspector"
        @edit="openEditor"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { SearchOutline } from '@vicons/ionicons5';
import {
  NAlert, NButton, NCollapse, NCollapseItem, NEmpty, NIcon, NInput,
  NRadioGroup, NRadioButton, NSpace, NSwitch, NSpin, NTag, NTooltip,
} from 'naive-ui';
import HeaderToolbar from '@/components/common/HeaderToolbar.vue';
import KnowledgeFileTree from './components/KnowledgeFileTree.vue';
import KnowledgeGraphCanvas from './components/KnowledgeGraphCanvas.vue';
import KnowledgeEditor from './components/KnowledgeEditor.vue';
import KnowledgeInspector from './components/KnowledgeInspector.vue';
import KnowledgeSearchResults from './components/KnowledgeSearchResults.vue';
import { useKnowledgePage } from './useKnowledgePage';

const {
  store,
  centerView,
  inspectorOpen,
  gatewayOnline,
  shortWorkspace,
  statusDetail,
  previewContent,
  selectedHitSnippet,
  showInspector,
  sidebarFiles,
  missingConfigFiles,
  hasMemoryElsewhere,
  showWorkspacePicker,
  formatBytes,
  workspaceLabel,
  refreshAll,
  onAutoDiscover,
  pickWorkspace,
  onSearch,
  onChunksToggle,
  onGraphNodeClick,
  onFileSelect,
  onSearchHit,
  clearInspector,
  openEditor,
  createWorkspaceFile,
  createMemory,
  createTodayDiary,
  saveCurrent,
  confirmDelete,
} = useKnowledgePage();
</script>

<style scoped lang="scss">
@use './knowledge.scss';
</style>
