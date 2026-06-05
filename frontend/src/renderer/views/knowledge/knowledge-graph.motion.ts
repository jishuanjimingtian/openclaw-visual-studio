import type { ECharts } from 'echarts';

type GraphNodeData = {
  id?: string;
  name?: string;
  x?: number;
  y?: number;
  fixed?: boolean;
  symbolSize?: number;
  itemStyle?: {
    shadowBlur?: number;
    opacity?: number;
    color?: string;
    borderColor?: string;
    borderWidth?: number;
  };
  label?: Record<string, unknown>;
  [key: string]: unknown;
};

type NodeSnapshot = {
  id: string;
  baseSize: number;
  baseShadow: number;
  baseItemStyle: GraphNodeData['itemStyle'];
  phase: number;
};

function phaseFromId(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i += 1) {
    h = (h * 31 + id.charCodeAt(i)) % 6283;
  }
  return h / 1000;
}

function readSeriesData(chart: ECharts): GraphNodeData[] {
  const series = (chart.getOption() as { series?: Array<{ data?: GraphNodeData[] }> }).series?.[0];
  return series?.data ?? [];
}

export interface KnowledgeGraphMotion {
  bind(chart: ECharts, container: HTMLElement): void;
  unbind(): void;
  onLayoutStart(): void;
  refreshSnapshots(): void;
}

export function createKnowledgeGraphMotion(isEnabled: () => boolean): KnowledgeGraphMotion {
  let chart: ECharts | null = null;
  let container: HTMLElement | null = null;
  let rafId = 0;
  let timeOrigin = 0;
  let snapshots: NodeSnapshot[] = [];
  let layoutSettling = true;
  let layoutReadyTimer = 0;
  let dragging = false;
  let bound = false;

  const onPointerDown = () => {
    dragging = true;
  };

  const onPointerUp = () => {
    if (!dragging) return;
    dragging = false;
    refreshSnapshots();
  };

  const markLayoutReady = () => {
    if (layoutReadyTimer) {
      window.clearTimeout(layoutReadyTimer);
      layoutReadyTimer = 0;
    }
    if (!layoutSettling) return;
    layoutSettling = false;
    refreshSnapshots();
  };

  const onFinished = () => {
    markLayoutReady();
  };

  const onVisibility = () => {
    if (document.hidden) {
      refreshSnapshots();
    } else {
      timeOrigin = performance.now();
    }
  };

  function refreshSnapshots() {
    if (!chart) return;
    const data = readSeriesData(chart);
    snapshots = data
      .filter((n) => {
        const opacity = n.itemStyle?.opacity;
        const id = n.id ?? n.name;
        return id != null && (opacity === undefined || opacity > 0.35);
      })
      .map((n) => {
        const id = String(n.id ?? n.name ?? '');
        const size = typeof n.symbolSize === 'number' ? n.symbolSize : 22;
        const shadow = n.itemStyle?.shadowBlur ?? 10;
        return {
          id,
          baseSize: size,
          baseShadow: shadow,
          baseItemStyle: n.itemStyle,
          phase: phaseFromId(id),
        };
      });
  }

  function applyFrame(now: number) {
    if (
      !chart
      || !isEnabled()
      || dragging
      || layoutSettling
      || document.hidden
      || snapshots.length === 0
    ) {
      return;
    }

    const t = (now - timeOrigin) / 1000;
    const updated = snapshots.map((s) => {
      const wave = Math.sin(t * 1.05 + s.phase);
      return {
        id: s.id,
        symbolSize: s.baseSize * (1 + 0.042 * wave),
        itemStyle: {
          ...s.baseItemStyle,
          shadowBlur: s.baseShadow * (1 + 0.16 * wave),
        },
      };
    });

    chart.setOption(
      { series: [{ id: 'knowledge-nebula', data: updated }] },
      { lazyUpdate: true, silent: true },
    );
  }

  function loop(now: number) {
    applyFrame(now);
    rafId = requestAnimationFrame(loop);
  }

  function startLoop() {
    if (rafId) return;
    timeOrigin = performance.now();
    rafId = requestAnimationFrame(loop);
  }

  function stopLoop() {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = 0;
    }
  }

  return {
    bind(nextChart, nextContainer) {
      if (bound) {
        chart?.off('finished', onFinished);
        container?.removeEventListener('pointerdown', onPointerDown);
        window.removeEventListener('pointerup', onPointerUp);
        window.removeEventListener('pointercancel', onPointerUp);
        document.removeEventListener('visibilitychange', onVisibility);
        stopLoop();
      }

      chart = nextChart;
      container = nextContainer;
      bound = true;
      layoutSettling = true;

      chart.on('finished', onFinished);
      container.addEventListener('pointerdown', onPointerDown);
      window.addEventListener('pointerup', onPointerUp);
      window.addEventListener('pointercancel', onPointerUp);
      document.addEventListener('visibilitychange', onVisibility);

      if (isEnabled()) {
        startLoop();
      }
    },

    unbind() {
      if (!bound) return;
      chart?.off('finished', onFinished);
      container?.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('pointerup', onPointerUp);
      window.removeEventListener('pointercancel', onPointerUp);
      document.removeEventListener('visibilitychange', onVisibility);
      if (layoutReadyTimer) {
        window.clearTimeout(layoutReadyTimer);
        layoutReadyTimer = 0;
      }
      stopLoop();
      chart = null;
      container = null;
      snapshots = [];
      bound = false;
      dragging = false;
      layoutSettling = true;
    },

    onLayoutStart() {
      layoutSettling = true;
      snapshots = [];
      if (layoutReadyTimer) {
        window.clearTimeout(layoutReadyTimer);
      }
      layoutReadyTimer = window.setTimeout(markLayoutReady, 1600);
    },

    refreshSnapshots,
  };
}
