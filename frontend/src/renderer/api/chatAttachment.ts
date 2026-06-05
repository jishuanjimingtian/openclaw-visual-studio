import { getApiBaseUrl } from '@shared/backend';
import type { ApiResponse, AttachmentInitResult, ChatAttachmentRef } from '@shared/types';
import apiClient, { ApiError } from './client';

const BASE_URL = getApiBaseUrl();

export const chatAttachmentApi = {
  init(name: string, mime: string, sizeBytes: number, sha256?: string) {
    return apiClient.post<AttachmentInitResult>('/openclaw/chat/attachments/init', {
      name,
      mime,
      sizeBytes,
      sha256,
    });
  },

  async uploadChunk(uploadId: string, index: number, blob: Blob, signal?: AbortSignal) {
    const url = `${BASE_URL}/openclaw/chat/attachments/${uploadId}/chunks/${index}`;
    const response = await fetch(url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: blob,
      signal,
    });
    const body = (await response.json().catch(() => null)) as ApiResponse<unknown> | null;
    if (!response.ok) {
      throw new ApiError(response.status, body?.message || `分片上传失败 (${index})`);
    }
    if (body && typeof body.code === 'number' && body.code !== 0) {
      throw new ApiError(body.code, body.message || '分片上传失败');
    }
  },

  complete(uploadId: string, sha256?: string, width?: number, height?: number) {
    return apiClient.post<ChatAttachmentRef>(`/openclaw/chat/attachments/${uploadId}/complete`, {
      sha256,
      width,
      height,
    });
  },

  attachmentUrl(attachmentId: string) {
    return `${BASE_URL}/openclaw/chat/attachments/${attachmentId}`;
  },

  thumbUrl(attachmentId: string) {
    return `${BASE_URL}/openclaw/chat/attachments/${attachmentId}/thumb`;
  },
};
