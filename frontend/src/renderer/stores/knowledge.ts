import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { knowledgeApi } from '@/api/knowledge';
import type {
  KnowledgeBrowseView,
  KnowledgeFile,
  KnowledgeFileContent,
  KnowledgeGraph,
  KnowledgeGraphEdge,
  KnowledgeGraphNode,
  KnowledgeOverview,
  KnowledgeSearchHit,
  KnowledgeSearchResult,
  KnowledgeWorkspaceCandidate,
} from '@shared/types';

const FILE_NODE_KINDS = new Set([
  'hub', 'daily', 'dream', 'dream_shard', 'soul', 'user', 'agents', 'unknown',
]);

export const useKnowledgeStore = defineStore('knowledge', () => {
  const loading = ref(false);
  const saving = ref(false);
  const searching = ref(false);
  const overview = ref<KnowledgeOverview | null>(null);
  const files = ref<KnowledgeFile[]>([]);
  const graph = ref<KnowledgeGraph | null>(null);
  const graphLoaded = ref(false);
  const overlayEdges = ref<KnowledgeGraphEdge[]>([]);
  const highlightNodeIds = ref<string[]>([]);
  const selectedNodeId = ref<string | null>(null);
  const selectedPath = ref<string | null>(null);
  const fileContent = ref<KnowledgeFileContent | null>(null);
  const contentCache = ref<Map<string, KnowledgeFileContent>>(new Map());
  const searchQuery = ref('');
  const searchResult = ref<KnowledgeSearchResult | null>(null);
  const browseView = ref<KnowledgeBrowseView>('graph');
  const includeChunks = ref(false);
  const editorDirty = ref(false);
  const editorContent = ref('');
  const indexRebuilding = ref(false);
  const loadError = ref<string | null>(null);
  const workspaceCandidates = ref<KnowledgeWorkspaceCandidate[]>([]);

  let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;

  const selectedNode = computed(() =>
    graph.value?.nodes.find((n) => n.id === selectedNodeId.value) ?? null,
  );

  const mergedEdges = computed(() => {
    const base = graph.value?.edges ?? [];
    const extra = overlayEdges.value ?? [];
    const map = new Map<string, KnowledgeGraphEdge>();
    for (const e of base) map.set(e.id, e);
    for (const e of extra) map.set(e.id, e);
    return [...map.values()];
  });

  const displayGraph = computed(() => {
    if (!graph.value) return null;
    return {
      ...graph.value,
      edges: mergedEdges.value,
    };
  });

  function syncFilesFromGraph() {
    const nodes = graph.value?.nodes ?? [];
    const fromGraph: KnowledgeFile[] = [];
    const seen = new Set<string>();
    for (const n of nodes) {
      if (!n.path || n.kind === 'chunk' || n.kind === 'topic') continue;
      if (!FILE_NODE_KINDS.has(String(n.kind))) continue;
      if (seen.has(n.path)) continue;
      seen.add(n.path);
      fromGraph.push({
        path: n.path,
        kind: n.kind,
        sizeBytes: n.size ?? 0,
      });
    }
    if (fromGraph.length === 0) return;
    if (files.value.length === 0) {
      files.value = fromGraph;
      return;
    }
    for (const f of fromGraph) {
      if (!files.value.some((x) => x.path === f.path)) {
        files.value.push(f);
      }
    }
  }

  async function fetchOverview(autoConfigure = false) {
    loading.value = true;
    try {
      const res = await knowledgeApi.getOverview(autoConfigure);
      overview.value = res.data;
      workspaceCandidates.value = res.data.candidates ?? [];
      loadError.value = null;
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '无法加载知识库概览';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function fetchFiles(autoConfigure = false) {
    const res = await knowledgeApi.listFiles(autoConfigure);
    files.value = Array.isArray(res.data) ? res.data : [];
    if (files.value.length === 0) {
      syncFilesFromGraph();
    }
  }

  async function fetchGraph(options?: { focusPath?: string; autoConfigure?: boolean; force?: boolean }) {
    if (graphLoaded.value && !options?.force && !options?.focusPath) {
      return;
    }
    loading.value = true;
    try {
      const res = await knowledgeApi.getGraph({
        includeChunks: includeChunks.value,
        autoConfigure: options?.autoConfigure ?? false,
        focusPath: options?.focusPath,
      });
      graph.value = res.data;
      graphLoaded.value = true;
      syncFilesFromGraph();
      loadError.value = null;
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '知识图谱加载失败';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function loadFile(path: string, autoConfigure = true) {
    const cached = contentCache.value.get(path);
    const fileMeta = files.value.find((f) => f.path === path);
    if (cached && fileMeta?.updatedAt && cached.updatedAt === fileMeta.updatedAt) {
      fileContent.value = cached;
      selectedPath.value = path;
      editorContent.value = cached.content;
      editorDirty.value = false;
      selectedNodeId.value = `file:${path}`;
      return;
    }
    loading.value = true;
    try {
      const res = await knowledgeApi.readFile(path, autoConfigure);
      fileContent.value = res.data;
      contentCache.value.set(path, res.data);
      selectedPath.value = path;
      editorContent.value = res.data.content;
      editorDirty.value = false;
      selectedNodeId.value = `file:${path}`;
    } finally {
      loading.value = false;
    }
  }

  async function saveFile(path: string, content: string, autoConfigure = true) {
    saving.value = true;
    try {
      const res = await knowledgeApi.writeFile(path, content, autoConfigure);
      fileContent.value = res.data;
      contentCache.value.set(path, res.data);
      editorContent.value = res.data.content;
      editorDirty.value = false;
      graphLoaded.value = false;
      await Promise.all([fetchFiles(false), fetchGraph({ autoConfigure: false, force: true })]);
    } finally {
      saving.value = false;
    }
  }

  async function deleteFile(path: string) {
    await knowledgeApi.deleteFile(path, true);
    contentCache.value.delete(path);
    graphLoaded.value = false;
    if (selectedPath.value === path) {
      selectedPath.value = null;
      fileContent.value = null;
      editorContent.value = '';
      selectedNodeId.value = null;
    }
    await Promise.all([fetchOverview(false), fetchFiles(false), fetchGraph({ autoConfigure: false, force: true })]);
  }

  async function runSearch(query?: string) {
    const q = (query ?? searchQuery.value).trim();
    if (!q) {
      searchResult.value = null;
      overlayEdges.value = [];
      highlightNodeIds.value = [];
      return;
    }
    searching.value = true;
    try {
      const res = await knowledgeApi.search(q, 20, false);
      searchResult.value = res.data;
      overlayEdges.value = res.data.graphOverlay?.edges ?? [];
      highlightNodeIds.value = res.data.graphOverlay?.highlightNodeIds ?? [];
    } finally {
      searching.value = false;
    }
  }

  function runSearchDebounced(query?: string, delayMs = 300) {
    if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      runSearch(query);
    }, delayMs);
  }

  function clearSearchOverlay() {
    searchResult.value = null;
    overlayEdges.value = [];
    highlightNodeIds.value = [];
  }

  function selectNode(node: KnowledgeGraphNode) {
    selectedNodeId.value = node.id;
    if (node.path) {
      selectedPath.value = node.path;
      loadFile(node.path);
    }
  }

  function selectSearchHit(hit: KnowledgeSearchHit) {
    if (hit.nodeId) selectedNodeId.value = hit.nodeId;
    if (hit.path) loadFile(hit.path);
  }

  async function rebuildIndex() {
    indexRebuilding.value = true;
    try {
      await knowledgeApi.rebuildIndex();
      await fetchOverview(false);
    } finally {
      indexRebuilding.value = false;
    }
  }

  async function discoverWorkspace(persistToConfig = false) {
    loading.value = true;
    try {
      const res = await knowledgeApi.discoverWorkspace(persistToConfig);
      overview.value = res.data.overview;
      workspaceCandidates.value = res.data.candidates ?? res.data.overview.candidates ?? [];
      loadError.value = null;
      return res.data;
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '自动查找工作区失败';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function switchWorkspace(path: string, persistToConfig = true) {
    loading.value = true;
    graphLoaded.value = false;
    contentCache.value.clear();
    try {
      const res = await knowledgeApi.useWorkspace(path, persistToConfig);
      overview.value = res.data;
      workspaceCandidates.value = res.data.candidates ?? [];
      await Promise.all([fetchFiles(false), fetchGraph({ autoConfigure: false, force: true })]);
      loadError.value = null;
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '切换工作区失败';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  async function bootstrap(includeGraph = true) {
    loadError.value = null;
    loading.value = true;
    try {
      const res = await knowledgeApi.bootstrap({
        autoConfigure: false,
        includeGraph,
        includeChunks: includeChunks.value,
      });
      overview.value = res.data.overview;
      files.value = res.data.files ?? [];
      workspaceCandidates.value = res.data.overview.candidates ?? [];
      if (res.data.graph) {
        graph.value = res.data.graph;
        graphLoaded.value = true;
      } else if (includeGraph) {
        await fetchGraph({ autoConfigure: false, force: true });
      }
    } catch (e) {
      try {
        await discoverWorkspace(false);
        await fetchFiles(false);
        if (includeGraph) {
          await fetchGraph({ autoConfigure: false, force: true });
        }
      } catch {
        loadError.value = e instanceof Error ? e.message : '知识库加载失败，请确认后端已启动';
      }
    } finally {
      loading.value = false;
    }
  }

  async function ensureGraph() {
    if (!graphLoaded.value) {
      await fetchGraph({ autoConfigure: false, force: true });
    }
  }

  return {
    loading,
    saving,
    searching,
    overview,
    files,
    graph,
    graphLoaded,
    displayGraph,
    overlayEdges,
    highlightNodeIds,
    selectedNodeId,
    selectedPath,
    selectedNode,
    fileContent,
    searchQuery,
    searchResult,
    browseView,
    includeChunks,
    editorDirty,
    editorContent,
    indexRebuilding,
    loadError,
    workspaceCandidates,
    discoverWorkspace,
    switchWorkspace,
    fetchOverview,
    fetchFiles,
    fetchGraph,
    loadFile,
    saveFile,
    deleteFile,
    runSearch,
    runSearchDebounced,
    clearSearchOverlay,
    selectNode,
    selectSearchHit,
    rebuildIndex,
    bootstrap,
    ensureGraph,
  };
});
