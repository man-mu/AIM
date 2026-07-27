/**
 * 单飞（single-flight）异步去重。
 *
 * 典型场景：多个请求同时收到 401，只发起一次 token 刷新，
 * 其余调用共享同一个 in-flight Promise；结束后自动复位。
 */
export function createSingleFlight<T>(task: () => Promise<T>): () => Promise<T> {
  let inFlight: Promise<T> | null = null;

  return () => {
    if (!inFlight) {
      inFlight = task().finally(() => {
        inFlight = null;
      });
    }
    return inFlight;
  };
}

/**
 * 按 key 去重的单飞：同 key 并发共享，不同 key 互不影响。
 * 典型场景：按 conversationId 的 settings 拉取、按 userId 的资料补全。
 */
export function createKeyedSingleFlight<K, T>(task: (key: K) => Promise<T>): (key: K) => Promise<T> {
  const inFlight = new Map<K, Promise<T>>();

  return (key: K) => {
    const existing = inFlight.get(key);
    if (existing) {
      return existing;
    }

    const next = task(key).finally(() => {
      inFlight.delete(key);
    });
    inFlight.set(key, next);
    return next;
  };
}
