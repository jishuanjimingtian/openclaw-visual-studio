import { existsSync, readFileSync } from 'fs';
import { join } from 'path';

export interface AppUpdateFeedConfig {
  provider: 'github' | 'generic';
  owner?: string;
  repo?: string;
  url?: string;
}

export type UpdateFeed =
  | { provider: 'generic'; url: string }
  | { provider: 'github'; owner: string; repo: string };

const DEFAULT_OWNER = 'jishuanjimingtian';
const DEFAULT_REPO = 'openclaw-visual-studio';
const DEFAULT_MIRRORS = ['https://ghfast.top/', 'https://mirror.ghproxy.com/'];

function normalizeBaseUrl(url: string): string {
  return url.endsWith('/') ? url : `${url}/`;
}

/** GitHub 最新 Release 资产根路径（正确写法，不是 tag 名为 latest） */
export function githubLatestReleaseFeedBase(owner: string, repo: string): string {
  return normalizeBaseUrl(`https://github.com/${owner}/${repo}/releases/latest/download`);
}

/** 修正旧版错误路径：/releases/download/latest/ → /releases/latest/download/ */
export function normalizeUpdateFeedUrl(url: string): string {
  return normalizeBaseUrl(url).replace(
    /\/releases\/download\/latest\/?/gi,
    '/releases/latest/download/',
  );
}

function isGithubReleaseFeedUrl(url: string): boolean {
  return /https:\/\/github\.com\/[^/]+\/[^/]+\/releases\//i.test(url);
}

function parseGithubReleaseFeedUrl(url: string): { owner: string; repo: string } | null {
  const match = url.match(/https:\/\/github\.com\/([^/]+)\/([^/]+)\/releases\//i);
  if (!match) return null;
  return { owner: match[1], repo: match[2] };
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
      return { provider, url: normalizeUpdateFeedUrl(parsed.url) };
    }
    if (parsed.owner && parsed.repo) {
      return { provider: 'github', owner: parsed.owner, repo: parsed.repo };
    }
    return null;
  } catch {
    return null;
  }
}

function buildMirrorFeeds(baseUrl: string, env: NodeJS.ProcessEnv): UpdateFeed[] {
  if (!isGithubReleaseFeedUrl(baseUrl)) {
    return [{ provider: 'generic', url: baseUrl }];
  }

  const mirrors = [
    env.UPDATE_GITHUB_MIRROR?.trim(),
    ...DEFAULT_MIRRORS,
  ].filter((value): value is string => Boolean(value));

  const seen = new Set<string>();
  const feeds: UpdateFeed[] = [];

  for (const mirror of mirrors) {
    const url = `${normalizeBaseUrl(mirror)}${baseUrl}`;
    if (seen.has(url)) continue;
    seen.add(url);
    feeds.push({ provider: 'generic', url });
  }

  if (!seen.has(baseUrl)) {
    feeds.push({ provider: 'generic', url: baseUrl });
  }

  const parsed = parseGithubReleaseFeedUrl(baseUrl);
  if (parsed) {
    feeds.push({ provider: 'github', owner: parsed.owner, repo: parsed.repo });
  }

  return feeds;
}

function buildGithubFeeds(owner: string, repo: string, env: NodeJS.ProcessEnv): UpdateFeed[] {
  const direct = githubLatestReleaseFeedBase(owner, repo);
  return buildMirrorFeeds(direct, env);
}

export function resolveUpdateFeeds(
  resourcesPath: string,
  env: NodeJS.ProcessEnv = process.env,
): UpdateFeed[] {
  const config = readAppUpdateConfig(resourcesPath);
  const seen = new Set<string>();
  const feeds: UpdateFeed[] = [];

  const pushFeed = (feed: UpdateFeed) => {
    const key = feed.provider === 'generic'
      ? `generic:${feed.url}`
      : `github:${feed.owner}/${feed.repo}`;
    if (seen.has(key)) return;
    seen.add(key);
    feeds.push(feed);
  };

  const pushFeeds = (items: UpdateFeed[]) => {
    for (const feed of items) {
      pushFeed(feed);
    }
  };

  // 始终优先使用正确的默认源（兼容旧版 app-update.yml 写错路径）
  pushFeeds(buildGithubFeeds(DEFAULT_OWNER, DEFAULT_REPO, env));

  if (config?.provider === 'generic' && config.url) {
    pushFeeds(buildMirrorFeeds(config.url, env));
  }

  if (config?.provider === 'github' && config.owner && config.repo) {
    pushFeeds(buildGithubFeeds(config.owner, config.repo, env));
  }

  return feeds;
}

export function formatUpdateError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err);
  if (/ERR_CONNECTION_RESET|ECONNRESET|ETIMEDOUT|ENOTFOUND|ECONNREFUSED|socket hang up|timeout/i.test(raw)) {
    return '无法连接更新服务器，请检查网络或稍后重试';
  }
  if (/404|not found|cannot find|ENOENT/i.test(raw)) {
    return '无法获取更新信息。请确认 Release 已正式发布，且 GitHub 仓库为公开（私有仓库客户端无法拉取）';
  }
  return raw;
}
