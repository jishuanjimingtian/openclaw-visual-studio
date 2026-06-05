import type { EChartsOption } from 'echarts';
import type { KnowledgeGraph, KnowledgeGraphEdge, KnowledgeGraphNode } from '@shared/types';

const NODE_COLORS_DARK: Record<string, string> = {
  hub: '#8b6dff',
  daily: '#3dd6c6',
  dream: '#a78bfa',
  dream_shard: '#c084fc',
  soul: '#f472b6',
  user: '#38bdf8',
  agents: '#94a3b8',
  chunk: '#5b8def',
  topic: '#f59e0b',
};

const NODE_COLORS_LIGHT: Record<string, string> = {
  hub: '#7c3aed',
  daily: '#0d9488',
  dream: '#7c3aed',
  dream_shard: '#9333ea',
  soul: '#db2777',
  user: '#0284c7',
  agents: '#64748b',
  chunk: '#2563eb',
  topic: '#d97706',
};

const EDGE_COLORS_DARK: Record<string, string> = {
  temporal: 'rgba(61, 214, 198, 0.55)',
  link: 'rgba(139, 109, 255, 0.75)',
  promote: 'rgba(245, 158, 11, 0.65)',
  tag: 'rgba(91, 141, 239, 0.5)',
  semantic: 'rgba(236, 72, 153, 0.85)',
};

const EDGE_COLORS_LIGHT: Record<string, string> = {
  temporal: 'rgba(13, 148, 136, 0.65)',
  link: 'rgba(124, 58, 237, 0.55)',
  promote: 'rgba(217, 119, 6, 0.6)',
  tag: 'rgba(37, 99, 235, 0.45)',
  semantic: 'rgba(219, 39, 119, 0.55)',
};

const THEME = {
  dark: {
    nodes: NODE_COLORS_DARK,
    edges: EDGE_COLORS_DARK,
    label: '#f1f5f9',
    labelDim: '#64748b',
    labelBorder: 'rgba(15, 23, 42, 0.9)',
    borderEmphasis: '#ffffff',
    borderNormal: 'rgba(255, 255, 255, 0.35)',
    tooltipBg: 'rgba(15, 23, 42, 0.94)',
    tooltipBorder: 'rgba(124, 92, 255, 0.45)',
    labelPosition: 'inside' as const,
  },
  light: {
    nodes: NODE_COLORS_LIGHT,
    edges: EDGE_COLORS_LIGHT,
    label: '#0f172a',
    labelDim: '#94a3b8',
    labelBorder: 'rgba(255, 255, 255, 0.95)',
    borderEmphasis: '#0f766e',
    borderNormal: 'rgba(255, 255, 255, 0.9)',
    tooltipBg: 'rgba(255, 255, 255, 0.98)',
    tooltipBorder: 'rgba(13, 148, 136, 0.4)',
    labelPosition: 'bottom' as const,
  },
} as const;

type GraphPalette = (typeof THEME)['light'] | (typeof THEME)['dark'];

function symbolSize(node: KnowledgeGraphNode): number {
  if (node.kind === 'hub') return 52;
  if (node.kind === 'agents') return 28;
  if (node.kind === 'soul' || node.kind === 'user') return 26;
  if (node.kind === 'daily') return 30;
  if (node.kind === 'dream' || node.kind === 'dream_shard') return 24;
  if (node.kind === 'topic') return 20;
  if (node.kind === 'chunk') return 12;
  return 22;
}

function nodeColor(node: KnowledgeGraphNode, palette: GraphPalette): string {
  return palette.nodes[node.kind] ?? '#64748b';
}

function labelStyle(
  node: KnowledgeGraphNode,
  palette: GraphPalette,
  dimmed: boolean,
) {
  const isLight = palette.labelPosition === 'bottom';
  return {
    show: true,
    position: node.kind === 'chunk' ? 'right' : palette.labelPosition,
    distance: isLight ? 10 : 4,
    fontSize: node.kind === 'hub' ? 13 : node.kind === 'chunk' ? 9 : 11,
    fontWeight: (node.kind === 'hub' ? 'bold' : '600') as 'bold' | 'normal' | '600',
    color: dimmed ? palette.labelDim : palette.label,
    textBorderColor: palette.labelBorder,
    textBorderWidth: isLight ? 3 : 2,
  };
}

function buildAdjacency(edges: KnowledgeGraphEdge[]) {
  const adj = new Map<string, Set<string>>();
  for (const e of edges) {
    if (!adj.has(e.source)) adj.set(e.source, new Set());
    if (!adj.has(e.target)) adj.set(e.target, new Set());
    adj.get(e.source)!.add(e.target);
    adj.get(e.target)!.add(e.source);
  }
  return adj;
}

function isNeighborHighlighted(
  nodeId: string,
  highlight: Set<string>,
  adjacency: Map<string, Set<string>>,
): boolean {
  if (!highlight.size) return false;
  const neighbors = adjacency.get(nodeId);
  if (!neighbors) return false;
  for (const n of neighbors) {
    if (highlight.has(n)) return true;
  }
  return false;
}

export function graphStructureKey(graph: KnowledgeGraph | null): string {
  if (!graph) return '';
  return `${graph.nodes.length}:${graph.edges.length}:${graph.meta.generatedAt}`;
}

export function buildKnowledgeGraphOption(
  graph: KnowledgeGraph,
  options?: {
    highlightNodeIds?: string[];
    focusNodeId?: string | null;
    reducedMotion?: boolean;
    colorMode?: 'light' | 'dark';
    /** 节点/边结构变化时拉长入场动画 */
    structureChanged?: boolean;
  },
): EChartsOption {
  const isLight = options?.colorMode === 'light';
  const palette = THEME[isLight ? 'light' : 'dark'];
  const highlight = new Set(options?.highlightNodeIds ?? []);
  const hasHighlight = highlight.size > 0;
  const focusId = options?.focusNodeId;
  const compact = graph.nodes.length <= 6;
  const adjacency = buildAdjacency(graph.edges);

  const categories = [
    { name: '长期记忆', itemStyle: { color: palette.nodes.hub } },
    { name: '每日笔记', itemStyle: { color: palette.nodes.daily } },
    { name: '梦境', itemStyle: { color: palette.nodes.dream } },
    { name: '工作区', itemStyle: { color: palette.nodes.agents } },
    { name: '条目', itemStyle: { color: palette.nodes.chunk } },
    { name: '主题', itemStyle: { color: palette.nodes.topic } },
  ];

  const categoryIndex = (kind: string) => {
    switch (kind) {
      case 'hub': return 0;
      case 'daily': return 1;
      case 'dream':
      case 'dream_shard': return 2;
      case 'soul':
      case 'user':
      case 'agents': return 3;
      case 'chunk': return 4;
      case 'topic': return 5;
      default: return 0;
    }
  };

  const hubNode = graph.nodes.find((n) => n.kind === 'hub');

  const nodes = graph.nodes.map((n) => {
    const emphasized = highlight.has(n.id) || n.id === focusId;
    const neighborHighlight = isNeighborHighlighted(n.id, highlight, adjacency);
    const dimmed = hasHighlight && !emphasized && !neighborHighlight;
    const size = emphasized ? symbolSize(n) * 1.2 : symbolSize(n);
    const fill = nodeColor(n, palette);

    const base: Record<string, unknown> = {
      id: n.id,
      name: n.label,
      category: categoryIndex(n.kind),
      symbolSize: size,
      value: n.size ?? size,
      itemStyle: {
        color: fill,
        borderColor: emphasized ? palette.borderEmphasis : palette.borderNormal,
        borderWidth: n.kind === 'hub' ? 2.5 : 1.5,
        opacity: dimmed ? 0.25 : 1,
        shadowBlur: isLight ? (n.kind === 'hub' ? 12 : 6) : (n.kind === 'hub' ? 24 : 10),
        shadowColor: isLight ? 'rgba(15, 23, 42, 0.12)' : fill,
      },
      label: labelStyle(n, palette, dimmed),
    };

    if (n.kind === 'hub' && compact) {
      base.x = 0;
      base.y = 0;
      base.fixed = true;
    }

    return base;
  });

  const lineType = (kind: string): 'solid' | 'dashed' | 'dotted' => {
    if (kind === 'tag') return 'dotted';
    if (kind === 'semantic') return 'dashed';
    return 'solid';
  };

  const links = graph.edges.map((e: KnowledgeGraphEdge) => {
    const dimmed = hasHighlight
      && !highlight.has(e.source)
      && !highlight.has(e.target);
    return {
      source: e.source,
      target: e.target,
      value: e.value ?? 0.5,
      lineStyle: {
        color: palette.edges[e.kind] ?? (isLight ? 'rgba(100, 116, 139, 0.4)' : 'rgba(255,255,255,0.25)'),
        width: Math.max(1.5, (e.value ?? 0.5) * (e.kind === 'link' ? 3.5 : 2)),
        type: lineType(e.kind),
        curveness: e.kind === 'semantic' ? 0.35 : 0.15,
        opacity: dimmed ? 0.12 : isLight ? 0.75 : 0.9,
      },
    };
  });

  const motionEnabled = !options?.reducedMotion;
  const entranceMs = options?.structureChanged ? 1200 : 800;

  return {
    backgroundColor: 'transparent',
    animationDuration: motionEnabled ? entranceMs : 0,
    animationDurationUpdate: motionEnabled ? 1100 : 0,
    animationEasingUpdate: 'cubicOut',
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.tooltipBg,
      borderColor: palette.tooltipBorder,
      borderWidth: 1,
      textStyle: { color: palette.label, fontSize: 12 },
      formatter: (p: unknown) => {
        const params = p as { dataType?: string; data?: { name?: string }; name?: string };
        if (params.dataType === 'edge') return '';
        return params.data?.name ?? params.name ?? '';
      },
    },
    series: [
      {
        id: 'knowledge-nebula',
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        focusNodeAdjacency: true,
        categories,
        data: nodes,
        links,
        center: hubNode ? undefined : ['50%', '50%'],
        force: {
          initLayout: hubNode ? 'circular' : undefined,
          repulsion: compact ? 240 : 340,
          gravity: hubNode ? 0.12 : 0.06,
          edgeLength: compact ? [80, 160] : [60, 140],
          friction: motionEnabled ? 0.28 : 0.35,
          layoutAnimation: motionEnabled,
        },
        emphasis: {
          focus: 'adjacency',
          scale: true,
          lineStyle: { width: 4, opacity: 1 },
          itemStyle: { shadowBlur: isLight ? 16 : 28 },
          label: {
            fontWeight: 'bold',
            color: palette.label,
            textBorderWidth: isLight ? 4 : 2,
          },
        },
        blur: {
          itemStyle: { opacity: 0.15 },
          lineStyle: { opacity: 0.08 },
        },
        lineStyle: { opacity: isLight ? 0.7 : 0.85, curveness: 0.15 },
      },
    ],
  } as EChartsOption;
}
