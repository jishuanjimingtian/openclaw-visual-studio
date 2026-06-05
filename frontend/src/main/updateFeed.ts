import { existsSync, readFileSync } from 'fs';
import { join } from 'path';

export interface AppUpdateFeedConfig {
  provider: 'github' | 'generic';
  owner?: string;
  repo?: string;
  url?: string;
}

export interface GenericUpdateFeed {
  provider: 'generic';
  url: string;
}

const DEFAULT_MIRRORS = ['https://ghfast.top/', 'https://mirror.ghproxy.com/'];

function normalizeBaseUrl(url: string): string {
  return url.endsWith('/') ? url : `${url}/`;
}

function githubReleaseFeedBase(owner: string, repo: string): string {
  return normalizeBaseUrl(`https://github.com/${owner}/${repo}/releases/download/latest`);
}

export function readAppUpdateConfig(resourcesPath: string): AppUpdateFeedConfig | null {
  const ymlPath = join(resourcesPath, 'app-update.yml');
  if (!existsSync(ymlPath)) {
    return null;
  }

  try {
    const raw = readFileSync(ymlPath, 'utf8');
    const parsed: Record<string, string> = {};
    for (const line of raw.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const colon = trimmed.indexOf(':');
      if (colon <= 0) continue;
      parsed[trimmed.slice(0, colon).trim()] = trimmed.slice(colon + 1).trim();
    }

    const provider = parsed.provider === 'generic' ? 'generic' : 'github';
    if (provider === 'generic' && parsed.url) {
      return { provider, url: normalizeBaseUrl(parsed.url) };
    }
    if (parsed.owner && parsed.repo) {
      return { provider: 'github', owner: parsed.owner, repo: parsed.repo };
    }
    return null;
  } catch {
    return null;
  }
}

export function resolveUpdateFeeds(
  resourcesPath: string,
  env: NodeJS.ProcessEnv = process.env,
): GenericUpdateFeed[] {
  const config = readAppUpdateConfig(resourcesPath);
  if (!config) return [];

  if (config.provider === 'generic' && config.url) {
    return [{ provider: 'generic', url: config.url }];
  }

  if (config.provider === 'github' && config.owner && config.repo) {
    const direct = githubReleaseFeedBase(config.owner, config.repo);
    const mirrors = [
      env.UPDATE_GITHUB_MIRROR?.trim(),
      ...DEFAULT_MIRRORS,
    ].filter((value): value is string => Boolean(value));

    const seen = new Set<string>();
    const feeds: GenericUpdateFeed[] = [];

    for (const mirror of mirrors) {
      const url = `${normalizeBaseUrl(mirror)}${direct}`;
      if (seen.has(url)) continue;
      seen.add(url);
      feeds.push({ provider: 'generic', url });
    }

    if (!seen.has(direct)) {
      feeds.push({ provider: 'generic', url: direct });
    }

    return feeds;
  }

  return [];
}

export function formatUpdateError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err);
  if (/ERR_CONNECTION_RESET|ECONNRESET|ETIMEDOUT|ENOTFOUND|ECONNREFUSED|socket hang up|timeout/i.test(raw)) {
    return '无法连接更新服务器，请检查网络或稍后重试';
  }
  if (/404|not found|cannot find|ENOENT/i.test(raw)) {
    return '暂未发布可用更新，请关注后续版本发布';
  }
  return raw;
}
