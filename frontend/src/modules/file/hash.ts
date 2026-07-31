/**
 * 文件哈希门面：优先走 Worker（不阻塞主线程），
 * 环境不支持（jsdom/旧浏览器）时回落到主线程 crypto.subtle。
 * Worker 为惰性单例，多次调用复用同一线程。
 */
let worker: Worker | null = null;
let sequence = 0;
const pending = new Map<number, { resolve: (hash: string) => void; reject: (error: Error) => void }>();

function getWorker(): Worker | null {
  if (worker) {
    return worker;
  }
  if (typeof Worker === 'undefined') {
    return null;
  }
  try {
    worker = new Worker(new URL('../../workers/hash.worker.ts', import.meta.url), { type: 'module' });
    worker.onmessage = (event: MessageEvent<{ id: number; hash?: string; error?: string }>) => {
      const { id, hash, error } = event.data;
      const entry = pending.get(id);
      if (!entry) {
        return;
      }
      pending.delete(id);
      if (hash) {
        entry.resolve(hash);
      } else {
        entry.reject(new Error(error ?? 'hash failed'));
      }
    };
    worker.onerror = () => {
      // Worker 崩溃：全部挂起项失败并重置，下次调用重建。
      for (const entry of pending.values()) {
        entry.reject(new Error('hash worker crashed'));
      }
      pending.clear();
      worker?.terminate();
      worker = null;
    };
    return worker;
  } catch {
    return null;
  }
}

async function hashOnMainThread(buffer: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

/** 计算文件 SHA-256（十六进制）。 */
export async function hashFileSha256(file: Blob): Promise<string> {
  const buffer = await file.arrayBuffer();
  const activeWorker = getWorker();
  if (!activeWorker) {
    return hashOnMainThread(buffer);
  }

  sequence += 1;
  const id = sequence;
  return new Promise<string>((resolve, reject) => {
    pending.set(id, { resolve, reject });
    // transferable：零拷贝移交 buffer 所有权。
    activeWorker.postMessage({ id, buffer }, [buffer]);
  });
}
