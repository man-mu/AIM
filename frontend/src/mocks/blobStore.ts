/**
 * Mock 文件字节存放处（仅运行时内存）。
 *
 * 三步上传中，PUT 到 `mock://upload/{fileId}` 的字节被 useFileUpload
 * 短路到这里；下载 URL 返回对应 objectURL，让图片/头像真实可见。
 * 头像走 dataURL（可持久化），消息附件走 objectURL（会话内有效）。
 */
const urls = new Map<string, string>();

export const mockBlobStore = {
  putBlob(fileId: string, blob: Blob): string {
    this.revoke(fileId);
    const url = URL.createObjectURL(blob);
    urls.set(fileId, url);
    return url;
  },
  putDataUrl(fileId: string, dataUrl: string): string {
    urls.set(fileId, dataUrl);
    return dataUrl;
  },
  getUrl(fileId: string): string | null {
    return urls.get(fileId) ?? null;
  },
  revoke(fileId: string): void {
    const existing = urls.get(fileId);
    if (existing && existing.startsWith('blob:')) {
      URL.revokeObjectURL(existing);
    }
    urls.delete(fileId);
  },
};

export function isMockUploadUrl(url: string): boolean {
  return url.startsWith('mock://upload/');
}

export function fileIdFromMockUploadUrl(url: string): string {
  return url.slice('mock://upload/'.length);
}
