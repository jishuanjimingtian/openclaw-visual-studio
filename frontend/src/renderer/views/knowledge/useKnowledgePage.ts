import { ref, computed, onMounted, watch } from 'vue';
import { useMessage, useDialog } from 'naive-ui';
import { useKnowledgeStore } from '@/stores/knowledge';
import { useGatewayStore } from '@/stores/gateway';
import { knowledgeApi } from '@/api/knowledge';
import type { KnowledgeSearchHit } from '@shared/types';

export type CenterView = 'graph' | 'edit';

const FILE_NODE_KINDS = new Set([
  'hub', 'daily', 'dream', 'dream_shard', 'soul', 'user', 'agents',
]);

export function useKnowledgePage() {
  const store = useKnowledgeStore();
  const gatewayStore = useGatewayStore();
  const message = useMessage();
  const dialog = useDialog();

  const centerView = ref<CenterView>('graph');
  const inspectorOpen = ref(true);

  const gatewayOnline = computed(
    () => store.overview?.gatewayConnected ?? gatewayStore.wsConnected,
  );

  const shortWorkspace = computed(() => {
    const p = store.overview?.workspacePath ?? '';
    if (p.length <= 40) return p || '—';
    return '…' + p.slice(-36);
  });

  const statusDetail = computed(() => {
    const o = store.overview;
    if (!o) return '';
    const parts: string[] = [];
    if (o.dailyNoteCount) parts.push(`${o.dailyNoteCount} 日记`);
    if (o.dreamShardCount) parts.push(`${o.dreamShardCount} 梦境片段`);
    if (o.workspaceConfigCount) parts.push(`${o.workspaceConfigCount} 工作区文件`);
    return parts.join(' · ');
  });

  const previewContent = computed(() => {
    const c = store.fileContent?.content ?? '';
    const max = 4000;
    return c.length > max ? c.slice(0, max) + '\n…' : c;
  });

  const selectedHitSnippet = computed(() => {
    const id = store.selectedNodeId;
    if (!id || !store.searchResult) return '';
    return store.searchResult.hits.find((h) => h.nodeId === id)?.snippet ?? '';
  });

  const showInspector = computed(
    () => inspectorOpen.value && !!(store.selectedNode || store.selectedPath),
  );

  const hasMemoryElsewhere = computed(() =>
    store.workspaceCandidates.some((c) => c.memoryFileCount > 0 && !c.active),
  );

  const sidebarFiles = computed(() => {
    const list = store.files ?? [];
    if (list.length > 0) return list;
    const fromGraph: typeof list = [];
    const seen = new Set<string>();
    for (const n of store.graph?.nodes ?? []) {
      if (!n.path || n.kind === 'chunk' || n.kind === 'topic') continue;
      if (!FILE_NODE_KINDS.has(String(n.kind))) continue;
      if (seen.has(n.path)) continue;
      seen.add(n.path);
      fromGraph.push({ path: n.path, kind: n.kind, sizeBytes: n.size ?? 0 });
    }
    return fromGraph;
  });

  const missingConfigFiles = computed(() => {
    const paths = new Set(store.files.map((f) => f.path.toUpperCase()));
    const out: { path: string; label: string; template: string }[] = [];
    if (!paths.has('SOUL.MD')) {
      out.push({ path: 'SOUL.md', label: 'SOUL', template: '# 灵魂\n\n' });
    }
    if (!paths.has('USER.MD')) {
      out.push({ path: 'USER.md', label: 'USER', template: '# 用户\n\n' });
    }
    if (!paths.has('AGENTS.MD')) {
      out.push({ path: 'AGENTS.md', label: 'AGENTS', template: '# AGENTS.md\n\n' });
    }
    return out;
  });

  const showWorkspacePicker = computed(() => {
    const withMemory = store.workspaceCandidates.filter((c) => c.memoryFileCount > 0);
    return withMemory.length > 1 || hasMemoryElsewhere.value;
  });

  function formatBytes(n: number) {
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  }

  function workspaceLabel(c: { path: string; source: string }) {
    if (c.path.includes('.openclaw\\workspace') || c.path.includes('.openclaw/workspace')) {
      return '默认工作区';
    }
    const parts = c.path.replace(/\\/g, '/').split('/');
    return parts[parts.length - 1] || c.source;
  }

  function deleteMessage(path: string) {
    const upper = path.toUpperCase();
    if (upper === 'MEMORY.MD') {
      return '删除 MEMORY.md 将清空 Agent 长期记忆，确定继续？';
    }
    if (upper === 'AGENTS.MD') {
      return '删除 AGENTS.md 后 OpenClaw 启动 bootstrap 将缺失；角色页 systemPrompt 可能仍保留旧副本。确定继续？';
    }
    if (upper === 'SOUL.MD' || upper === 'USER.MD') {
      return `删除 ${path} 后 Agent 运行时上下文可能变空，确定继续？`;
    }
    return `确定删除 ${path}？`;
  }

  async function refreshAll() {
    await store.bootstrap(true);
    message.success('已刷新');
  }

  async function onAutoDiscover(persist = false) {
    try {
      const result = await store.discoverWorkspace(persist);
      await store.bootstrap(true);
      message.success(result.memoryFileCount > 0
        ? `已定位工作区（${result.memoryFileCount} 个记忆文件）`
        : '已扫描，未发现记忆文件');
    } catch {
      message.error(store.loadError ?? '定位失败');
    }
  }

  async function pickWorkspace(path: string) {
    try {
      await store.switchWorkspace(path, true);
      message.success('已切换工作区');
    } catch {
      message.error(store.loadError ?? '切换失败');
    }
  }

  async function onSearch() {
    await store.runSearch();
    centerView.value = 'graph';
  }

  function onChunksToggle() {
    store.graphLoaded = false;
    store.fetchGraph({ autoConfigure: false, force: true });
  }

  function onGraphNodeClick(nodeId: string) {
    const node = store.graph?.nodes.find((n) => n.id === nodeId);
    if (node) {
      store.selectNode(node);
      inspectorOpen.value = true;
    }
  }

  function onFileSelect(path: string) {
    store.loadFile(path);
    store.selectedNodeId = `file:${path}`;
    inspectorOpen.value = true;
  }

  function onSearchHit(hit: KnowledgeSearchHit) {
    store.selectSearchHit(hit);
    centerView.value = 'graph';
    inspectorOpen.value = true;
  }

  function clearInspector() {
    inspectorOpen.value = false;
  }

  function openEditor() {
    centerView.value = 'edit';
    if (!store.selectedPath && store.files.length > 0) {
      const hub = store.files.find((f) => f.kind === 'hub');
      store.loadFile((hub ?? store.files[0]).path);
    }
  }

  async function createWorkspaceFile(item: { path: string; template: string }) {
    store.selectedPath = item.path;
    store.editorContent = item.template;
    await store.saveFile(item.path, item.template);
    centerView.value = 'edit';
    message.success(`已创建 ${item.path}`);
  }

  async function createMemory() {
    const path = 'MEMORY.md';
    const content = '# 长期记忆\n\n';
    store.selectedPath = path;
    store.editorContent = content;
    await store.saveFile(path, content);
    centerView.value = 'edit';
  }

  async function createTodayDiary() {
    const res = await knowledgeApi.getTodayDiaryPath();
    const path = res.data;
    let content = '';
    try {
      content = (await knowledgeApi.readFile(path, false)).data.content;
    } catch {
      content = `# ${path.replace('memory/', '').replace('.md', '')}\n\n`;
    }
    store.selectedPath = path;
    store.editorContent = content;
    await store.saveFile(path, content);
    centerView.value = 'edit';
  }

  async function saveCurrent() {
    if (!store.selectedPath) return;
    await store.saveFile(store.selectedPath, store.editorContent);
    message.success('已保存');
  }

  function confirmDelete() {
    const path = store.selectedPath;
    if (!path) return;
    dialog.warning({
      title: '删除记忆文件',
      content: deleteMessage(path),
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        await store.deleteFile(path);
        message.success('已删除');
      },
    });
  }

  watch(centerView, async (v) => {
    if (v === 'graph') {
      await store.ensureGraph();
    }
    if (v === 'edit' && !store.selectedPath && store.files.length > 0) {
      const hub = store.files.find((f) => f.kind === 'hub');
      store.loadFile((hub ?? store.files[0]).path);
    }
  });

  onMounted(() => store.bootstrap(true));

  return {
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
  };
}
