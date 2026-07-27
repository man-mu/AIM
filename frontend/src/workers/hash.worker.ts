/// <reference lib="webworker" />
/**
 * 文件哈希 Worker：SHA-256（crypto.subtle）在独立线程计算，
 * 大文件（最高 100MB）哈希不阻塞主线程的输入与滚动。
 *
 * 协议：{ id, buffer: ArrayBuffer } → { id, hash } | { id, error }
 */
interface HashRequest {
  id: number;
  buffer: ArrayBuffer;
}

type HashResponse = { id: number; hash: string } | { id: number; error: string };

function toHex(digest: ArrayBuffer): string {
  const bytes = new Uint8Array(digest);
  let hex = '';
  for (let i = 0; i < bytes.length; i += 1) {
    hex += (bytes[i] as number).toString(16).padStart(2, '0');
  }
  return hex;
}

self.onmessage = async (event: MessageEvent<HashRequest>) => {
  const { id, buffer } = event.data;
  try {
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    const response: HashResponse = { id, hash: toHex(digest) };
    self.postMessage(response);
  } catch (error) {
    const response: HashResponse = { id, error: error instanceof Error ? error.message : 'hash failed' };
    self.postMessage(response);
  }
};

export {};
