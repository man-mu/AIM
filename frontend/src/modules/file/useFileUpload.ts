import { useCallback } from 'react';
import { fileApi } from '@/apis/file';
import { fileIdFromMockUploadUrl, isMockUploadUrl, mockBlobStore } from '@/mocks/blobStore';
import { toInt64String } from '@/lib/ids';
import type { FileAccess, FileInfo, FilePurpose } from '@/types/File/File';
import { hashFileSha256 } from './hash';

/**
 * 三步上传（upload-url → PUT → confirm）封装：
 *  - 进度回调（XHR upload.onprogress）；
 *  - AbortSignal 取消；
 *  - Worker 线程 SHA-256（完整性摘要；后端 md5 字段可选，暂不传，见 api-feedback）；
 *  - mock 预签名 URL（mock://）短路到 blobStore，图片消息/头像真实可见。
 */
export interface UploadOptions {
  purpose: FilePurpose;
  access: FileAccess;
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

export interface UploadOutcome {
  file: FileInfo;
  /** 可直接渲染的 URL（mock 为 objectURL；真实后端经 download 接口获取）。 */
  url: string;
  sha256: string;
}

function putToPresignedUrl(url: string, file: Blob, options: UploadOptions): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.upload.onprogress = (event) => {
      if (event.total > 0) {
        options.onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`上传失败（HTTP ${xhr.status}）`));
      }
    };
    xhr.onerror = () => reject(new Error('上传失败，请检查网络'));
    xhr.onabort = () => reject(new DOMException('aborted', 'AbortError'));
    options.signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(file);
  });
}

/** mock 上传：写入 blobStore 并以两帧进度模拟。 */
async function putToMockUrl(url: string, file: Blob, options: UploadOptions): Promise<string> {
  const fileId = fileIdFromMockUploadUrl(url);
  options.onProgress?.(35);
  await new Promise((resolve) => setTimeout(resolve, 120));
  if (options.signal?.aborted) {
    throw new DOMException('aborted', 'AbortError');
  }
  const objectUrl = mockBlobStore.putBlob(fileId, file);
  options.onProgress?.(90);
  return objectUrl;
}

export async function uploadFile(file: File, options: UploadOptions): Promise<UploadOutcome> {
  const sha256 = await hashFileSha256(file);
  if (options.signal?.aborted) {
    throw new DOMException('aborted', 'AbortError');
  }

  const grant = await fileApi.getUploadUrl({
    name: file.name,
    mimeType: file.type || 'application/octet-stream',
    size: file.size,
    purpose: options.purpose,
    access: options.access,
  });

  let localUrl: string;
  if (isMockUploadUrl(grant.uploadUrl)) {
    localUrl = await putToMockUrl(grant.uploadUrl, file, options);
  } else {
    await putToPresignedUrl(grant.uploadUrl, file, options);
    localUrl = URL.createObjectURL(file);
  }

  const confirmed = await fileApi.confirmUpload({ fileId: toInt64String(grant.fileId) });
  options.onProgress?.(100);
  return { file: confirmed.file, url: localUrl, sha256 };
}

export function useFileUpload() {
  return useCallback(uploadFile, []);
}
