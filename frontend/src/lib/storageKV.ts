/**
 * 面向 Web Storage 的安全 JSON KV 封装。
 *
 * - 注入 StorageLike 而非直接引用 localStorage（SSR/测试/隐私模式安全）；
 * - 读写全部 try/catch：损坏数据回退默认值，写满（QuotaExceeded）静默降级；
 * - 命名空间 + 版本号：schema 变更时旧数据整体失效，避免脏结构渗入。
 */
export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface JsonKV {
  read<T>(key: string, fallback: T): T;
  write<T>(key: string, value: T): void;
  remove(key: string): void;
}

export function getSafeLocalStorage(): StorageLike | null {
  try {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    const probe = '__aim_probe__';
    localStorage.setItem(probe, '1');
    localStorage.removeItem(probe);
    return localStorage;
  } catch {
    return null;
  }
}

const memoryFallback = (): StorageLike => {
  const map = new Map<string, string>();
  return {
    getItem: (key) => (map.has(key) ? (map.get(key) as string) : null),
    setItem: (key, value) => void map.set(key, value),
    removeItem: (key) => void map.delete(key),
  };
};

export function createJsonKV(namespace: string, version: number, backing?: StorageLike | null): JsonKV {
  const store = backing ?? getSafeLocalStorage() ?? memoryFallback();
  const prefix = `${namespace}:v${version}:`;

  return {
    read(key, fallback) {
      try {
        const raw = store.getItem(prefix + key);
        if (raw === null) {
          return fallback;
        }
        return JSON.parse(raw) as typeof fallback;
      } catch {
        return fallback;
      }
    },
    write(key, value) {
      try {
        store.setItem(prefix + key, JSON.stringify(value));
      } catch {
        // 存储满 / 被禁用：静默降级为“仅内存态”，不影响主流程。
      }
    },
    remove(key) {
      try {
        store.removeItem(prefix + key);
      } catch {
        // 同上。
      }
    },
  };
}
