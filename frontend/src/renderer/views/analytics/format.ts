export function formatNum(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

export function formatDelta(n: number | null): string {
  if (n === null) return '—';
  const sign = n > 0 ? '+' : '';
  return `${sign}${formatNum(Math.abs(n))}`;
}

export function formatCost(cost?: number | null): string {
  if (cost == null || Number.isNaN(cost)) return '—';
  return cost < 0.01 ? '<$0.01' : `$${cost.toFixed(2)}`;
}

export function shortModel(model: string): string {
  const parts = model.split('/');
  return parts.length > 1 ? parts[parts.length - 1] : model;
}

export function formatUpdatedAt(iso?: string | null): string {
  if (!iso) return '—';
  const d = iso.slice(0, 10);
  return d.length >= 10 ? d.slice(5) : iso;
}

export function sourceLabel(source: string): string {
  return source === 'gateway' ? 'Gateway' : '本地';
}
