import { createHash } from 'crypto';
import { createWriteStream, mkdirSync, readFileSync, unlinkSync } from 'fs';
import { dirname } from 'path';
import { net } from 'electron';
import type { AppUpdateProgress } from '@shared/update';
import {
  buildAssetDownloadCandidates,
  githubLatestReleaseFeedBase,
  normalizeUpdateFeedUrl,
  parseGithubReleaseFeedUrl,
  type UpdateFeed,
} from './updateFeed';

const DEFAULT_OWNER = 'jishuanjimingtian';
const DEFAULT_REPO = 'openclaw-visual-studio';
const FETCH_TIMEOUT_MS = 20_000;
const DOWNLOAD_TIMEOUT_MS = 20 * 60_000;
const STALL_TIMEOUT_MS = 15_000;

export interface LatestUpdateInfo {
  version: string;
  assetPath: string;
  sha512: string | null;
  size: number;
  githubAssetUrl: string;
}

function fetchText(url: string, timeoutMs = FETCH_TIMEOUT_MS): Promise<string> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      request.abort();
      reject(new Error('请求超时'));
    }, timeoutMs);

    const request = net.request({ url, redirect: 'follow' });
    const chunks: Buffer[] = [];

    request.on('response', (response) => {
      if (response.statusCode && response.statusCode >= 400) {
        clearTimeout(timer);
        reject(new Error(`HTTP ${response.statusCode}`));
        return;
      }

      response.on('data', (chunk: Buffer) => chunks.push(chunk));
      response.on('end', () => {
        clearTimeout(timer);
        resolve(Buffer.concat(chunks).toString('utf8'));
      });
      response.on('error', (err) => {
        clearTimeout(timer);
        reject(err);
      });
    });

    request.on('error', (err) => {
      clearTimeout(timer);
      reject(err);
    });
    request.end();
  });
}

function parseLatestYml(
  raw: string,
  owner: string,
  repo: string,
  feedBase: string,
): LatestUpdateInfo {
  const lines = raw.split('\n');
  let version = '';
  let pathValue = '';
  let sha512: string | null = null;
  let size = 0;
  let inFiles = false;
  let fileUrl = '';

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    if (trimmed === 'files:') {
      inFiles = true;
      continue;
    }

    if (inFiles) {
      if (trimmed.startsWith('- ')) continue;
      if (!trimmed.startsWith(' ')) {
        inFiles = false;
      } else {
        const fileMatch = trimmed.match(/^(url|sha512|size):\s*(.+)$/);
        if (fileMatch?.[1] === 'url') fileUrl = fileMatch[2].trim();
        if (fileMatch?.[1] === 'sha512' && !sha512) sha512 = fileMatch[2].trim();
        if (fileMatch?.[1] === 'size' && !size) size = Number(fileMatch[2].trim()) || 0;
        continue;
      }
    }

    const match = trimmed.match(/^(version|path|sha512|size):\s*(.+)$/);
    if (!match) continue;
    if (match[1] === 'version') version = match[2].trim();
    if (match[1] === 'path') pathValue = match[2].trim();
    if (match[1] === 'sha512') sha512 = match[2].trim();
    if (match[1] === 'size') size = Number(match[2].trim()) || 0;
  }

  const assetPath = pathValue || fileUrl;
  if (!version || !assetPath) {
    throw new Error('latest.yml 格式无效');
  }

  let githubAssetUrl = assetPath;
  if (!/^https?:\/\//i.test(assetPath)) {
    const base = normalizeUpdateFeedUrl(feedBase);
    const parsed = parseGithubReleaseFeedUrl(base);
    const ghOwner = parsed?.owner ?? owner;
    const ghRepo = parsed?.repo ?? repo;
    githubAssetUrl = `${githubLatestReleaseFeedBase(ghOwner, ghRepo)}${assetPath}`;
  }

  return { version, assetPath, sha512, size, githubAssetUrl };
}

function verifySha512(filePath: string, expected: string): void {
  const hash = createHash('sha512').update(readFileSync(filePath)).digest('base64');
  if (hash !== expected) {
    throw new Error('安装包校验失败，请重试');
  }
}

function downloadFile(
  url: string,
  dest: string,
  onProgress?: (progress: AppUpdateProgress) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    mkdirSync(dirname(dest), { recursive: true });

    let settled = false;
    let transferred = 0;
    let total = 0;
    let lastBytes = 0;
    let lastTime = Date.now();
    let stallTimer: NodeJS.Timeout | undefined;
    let totalTimer: NodeJS.Timeout | undefined;
    let request: Electron.ClientRequest | undefined;
    let file = createWriteStream(dest);

    const cleanup = () => {
      if (stallTimer) clearTimeout(stallTimer);
      if (totalTimer) clearTimeout(totalTimer);
    };

    const fail = (err: Error) => {
      if (settled) return;
      settled = true;
      cleanup();
      try {
        file.destroy();
        request?.abort();
        unlinkSync(dest);
      } catch {
        /* ignore */
      }
      reject(err);
    };

    const resetStall = () => {
      if (stallTimer) clearTimeout(stallTimer);
      stallTimer = setTimeout(() => fail(new Error('当前线路下载停滞')), STALL_TIMEOUT_MS);
    };

    request = net.request({ url, redirect: 'follow' });
    totalTimer = setTimeout(() => fail(new Error('下载超时')), DOWNLOAD_TIMEOUT_MS);

    request.on('response', (response) => {
      if (response.statusCode && response.statusCode >= 400) {
        fail(new Error(`HTTP ${response.statusCode}`));
        return;
      }

      total = Number.parseInt(String(response.headers['content-length'] ?? '0'), 10) || 0;
      resetStall();

      response.on('data', (chunk: Buffer) => {
        transferred += chunk.length;
        const now = Date.now();
        const elapsed = (now - lastTime) / 1000;
        const bytesPerSecond = elapsed > 0 ? (transferred - lastBytes) / elapsed : 0;
        lastBytes = transferred;
        lastTime = now;
        resetStall();
        file.write(chunk);
        onProgress?.({
          percent: total > 0 ? (transferred / total) * 100 : 0,
          transferred,
          total,
          bytesPerSecond,
        });
      });

      response.on('end', () => file.end());
      file.on('finish', () => {
        if (settled) return;
        settled = true;
        cleanup();
        resolve();
      });
      file.on('error', fail);
      response.on('error', fail);
    });

    request.on('error', fail);
    request.end();
  });
}

async function fetchLatestUpdateInfo(feed: UpdateFeed): Promise<LatestUpdateInfo> {
  if (feed.provider === 'github') {
    const base = githubLatestReleaseFeedBase(feed.owner, feed.repo);
    const yml = await fetchText(`${base}latest.yml`);
    return parseLatestYml(yml, feed.owner, feed.repo, base);
  }

  const base = normalizeUpdateFeedUrl(feed.url);
  const yml = await fetchText(`${base}latest.yml`);
  const parsed = parseGithubReleaseFeedUrl(base);
  return parseLatestYml(
    yml,
    parsed?.owner ?? DEFAULT_OWNER,
    parsed?.repo ?? DEFAULT_REPO,
    base,
  );
}

export async function downloadUpdateWithFeeds(
  feeds: UpdateFeed[],
  dest: string,
  onProgress?: (progress: AppUpdateProgress) => void,
): Promise<LatestUpdateInfo> {
  let lastError: unknown = null;

  for (const feed of feeds) {
    let info: LatestUpdateInfo;
    try {
      info = await fetchLatestUpdateInfo(feed);
    } catch (err) {
      lastError = err;
      continue;
    }

    const candidates = buildAssetDownloadCandidates(info.githubAssetUrl);
    for (const url of candidates) {
      try {
        await downloadFile(url, dest, onProgress);
        if (info.sha512) {
          verifySha512(dest, info.sha512);
        }
        return info;
      } catch (err) {
        lastError = err;
      }
    }
  }

  throw lastError instanceof Error ? lastError : new Error('所有下载线路均失败');
}
