/**
 * 受控并发的批量异步映射：
 * 会话列表批量拉取 settings 等 N+1 场景使用，避免瞬时打爆网络/后端。
 */
export async function mapWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  task: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const limit = Math.max(1, Math.floor(concurrency));
  const results = new Array<R>(items.length);
  let cursor = 0;

  async function worker(): Promise<void> {
    for (;;) {
      const index = cursor;
      cursor += 1;
      if (index >= items.length) {
        return;
      }
      results[index] = await task(items[index] as T, index);
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

/** 逐项容错版本：单项失败以 fallback 兜底，不影响整体。 */
export async function mapWithConcurrencySettled<T, R>(
  items: readonly T[],
  concurrency: number,
  task: (item: T, index: number) => Promise<R>,
  fallback: (item: T, error: unknown) => R,
): Promise<R[]> {
  return mapWithConcurrency(items, concurrency, async (item, index) => {
    try {
      return await task(item, index);
    } catch (error) {
      return fallback(item, error);
    }
  });
}
