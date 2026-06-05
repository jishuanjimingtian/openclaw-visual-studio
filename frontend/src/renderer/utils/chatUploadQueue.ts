import { chatAttachmentApi } from '@/api/chatAttachment';
import { inferAttachmentType } from '@/utils/chatParts';
import type { AttachmentPart, ChatAttachmentRef } from '@shared/types';

const MAX_FILE_BYTES = 100 * 1024 * 1024;
const MAX_ATTACHMENTS = 10;
const MAX_CONCURRENT_CHUNKS = 4;
const CHUNK_SIZE = 4 * 1024 * 1024;
const IMAGE_MAX_EDGE = 2048;
const IMAGE_COMPRESS_THRESHOLD = 2 * 1024 * 1024;

const BLOCKED_EXT = /\.(exe|bat|cmd|com|msi|dll|sh|bash|ps1|vbs|scr|jar)$/i;

export interface PendingAttachment {
  id: string;
  file: File;
  previewUrl: string;
  mime: string;
  name: string;
  sizeBytes: number;
  width?: number;
  height?: number;
  progress: number;
  state: 'pending' | 'uploading' | 'ready' | 'error';
  error?: string;
  ref?: ChatAttachmentRef;
}

function validateFile(file: File) {
  if (file.size <= 0 || file.size > MAX_FILE_BYTES) {
    throw new Error('文件大小必须在 1B ~ 100MB 之间');
  }
  if (BLOCKED_EXT.test(file.name)) {
    throw new Error('不允许上传可执行文件');
  }
  const mime = file.type || 'application/octet-stream';
  const ok =
    mime.startsWith('image/') ||
    mime.startsWith('text/') ||
    mime === 'application/pdf' ||
    mime === 'application/csv' ||
    mime === 'text/csv' ||
    mime === 'text/markdown' ||
    mime === 'text/x-markdown';
  if (!ok) {
    throw new Error(`不支持的文件类型: ${mime || file.name}`);
  }
}

async function sha256Hex(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

async function readImageSize(file: File): Promise<{ width: number; height: number } | null> {
  if (!file.type.startsWith('image/')) return null;
  try {
    const bitmap = await createImageBitmap(file);
    const size = { width: bitmap.width, height: bitmap.height };
    bitmap.close();
    return size;
  } catch {
    return null;
  }
}

async function maybeCompressImage(file: File): Promise<{ blob: Blob; width?: number; height?: number }> {
  const size = await readImageSize(file);
  const needsCompress =
    file.size > IMAGE_COMPRESS_THRESHOLD ||
    (size != null && Math.max(size.width, size.height) > IMAGE_MAX_EDGE);

  if (!needsCompress || !file.type.startsWith('image/')) {
    return { blob: file, width: size?.width, height: size?.height };
  }

  const bitmap = await createImageBitmap(file);
  const maxEdge = Math.max(bitmap.width, bitmap.height);
  const scale = Math.min(1, IMAGE_MAX_EDGE / maxEdge);
  const targetW = Math.max(1, Math.round(bitmap.width * scale));
  const targetH = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = new OffscreenCanvas(targetW, targetH);
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    return { blob: file, width: size?.width, height: size?.height };
  }
  ctx.drawImage(bitmap, 0, 0, targetW, targetH);
  bitmap.close();

  const mime = file.type === 'image/png' ? 'image/png' : 'image/jpeg';
  const blob = await canvas.convertToBlob({ type: mime, quality: 0.85 });
  return { blob, width: targetW, height: targetH };
}

async function uploadBlob(
  name: string,
  mime: string,
  blob: Blob,
  width?: number,
  height?: number,
  onProgress?: (p: number) => void,
): Promise<ChatAttachmentRef> {
  const sha256 = await sha256Hex(blob);
  const initRes = await chatAttachmentApi.init(name, mime, blob.size, sha256);
  const { uploadId, chunkSize } = initRes.data;
  const totalChunks = Math.ceil(blob.size / chunkSize);
  const uploaded = new Set(initRes.data.uploadedChunks ?? []);
  let completed = uploaded.size;

  const indices: number[] = [];
  for (let i = 0; i < totalChunks; i++) {
    if (!uploaded.has(i)) indices.push(i);
  }

  const report = () => onProgress?.(Math.min(99, Math.round((completed / totalChunks) * 100)));

  async function uploadOne(index: number) {
    const start = index * chunkSize;
    const end = Math.min(blob.size, start + chunkSize);
    const chunk = blob.slice(start, end);
    await chatAttachmentApi.uploadChunk(uploadId, index, chunk);
    completed += 1;
    report();
  }

  for (let i = 0; i < indices.length; i += MAX_CONCURRENT_CHUNKS) {
    const batch = indices.slice(i, i + MAX_CONCURRENT_CHUNKS);
    await Promise.all(batch.map((idx) => uploadOne(idx)));
  }

  const refRes = await chatAttachmentApi.complete(uploadId, sha256, width, height);
  onProgress?.(100);
  return refRes.data;
}

export class ChatUploadQueue {
  private items: PendingAttachment[] = [];

  get pending(): readonly PendingAttachment[] {
    return this.items;
  }

  get readyCount(): number {
    return this.items.filter((i) => i.state === 'ready').length;
  }

  get hasUploading(): boolean {
    return this.items.some((i) => i.state === 'uploading' || i.state === 'pending');
  }

  clear() {
    for (const item of this.items) {
      URL.revokeObjectURL(item.previewUrl);
    }
    this.items = [];
  }

  remove(id: string) {
    const item = this.items.find((i) => i.id === id);
    if (item) URL.revokeObjectURL(item.previewUrl);
    this.items = this.items.filter((i) => i.id !== id);
  }

  async addFiles(files: FileList | File[]) {
    const list = Array.from(files);
    if (this.items.length + list.length > MAX_ATTACHMENTS) {
      throw new Error(`单条消息最多 ${MAX_ATTACHMENTS} 个附件`);
    }
    for (const file of list) {
      validateFile(file);
      const previewUrl = URL.createObjectURL(file);
      this.items.push({
        id: crypto.randomUUID(),
        file,
        previewUrl,
        mime: file.type || 'application/octet-stream',
        name: file.name,
        sizeBytes: file.size,
        progress: 0,
        state: 'pending',
      });
    }
  }

  async flush(): Promise<AttachmentPart[]> {
    const parts: AttachmentPart[] = [];
    for (const item of this.items) {
      if (item.state === 'ready' && item.ref) {
        parts.push({
          type: inferAttachmentType(item.ref.mime),
          attachmentId: item.ref.attachmentId,
          name: item.ref.name,
          mime: item.ref.mime,
          sizeBytes: item.ref.sizeBytes,
          width: item.ref.width,
          height: item.ref.height,
          thumbReady: item.ref.thumbReady,
        });
        continue;
      }
      item.state = 'uploading';
      item.progress = 0;
      try {
        const { blob, width, height } = await maybeCompressImage(item.file);
        item.width = width;
        item.height = height;
        const ref = await uploadBlob(
          item.name,
          item.mime,
          blob,
          width,
          height,
          (p) => {
            item.progress = p;
          },
        );
        item.ref = ref;
        item.state = 'ready';
        item.progress = 100;
        parts.push({
          type: inferAttachmentType(ref.mime),
          attachmentId: ref.attachmentId,
          name: ref.name,
          mime: ref.mime,
          sizeBytes: ref.sizeBytes,
          width: ref.width,
          height: ref.height,
          thumbReady: ref.thumbReady,
        });
      } catch (e) {
        item.state = 'error';
        item.error = e instanceof Error ? e.message : '上传失败';
        throw e;
      }
    }
    return parts;
  }

  readyParts(): AttachmentPart[] {
    return this.items
      .filter((i) => i.state === 'ready' && i.ref)
      .map((i) => ({
        type: inferAttachmentType(i.ref!.mime),
        attachmentId: i.ref!.attachmentId,
        name: i.ref!.name,
        mime: i.ref!.mime,
        sizeBytes: i.ref!.sizeBytes,
        width: i.ref!.width,
        height: i.ref!.height,
        thumbReady: i.ref!.thumbReady,
      }));
  }
}
