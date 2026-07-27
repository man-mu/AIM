/**
 * 指数退避策略（带抖动），用于 WebSocket 重连与失败重试。
 * 随机源可注入，保证测试确定性。
 */
export interface BackoffOptions {
  baseMs: number;
  maxMs: number;
  factor?: number;
  /** 抖动比例 0~1：实际延迟在 [delay*(1-jitter), delay] 内取值。 */
  jitter?: number;
  random?: () => number;
}

export interface BackoffPolicy {
  /** attempt 从 1 开始。 */
  delayFor(attempt: number): number;
}

export function createBackoffPolicy(options: BackoffOptions): BackoffPolicy {
  const { baseMs, maxMs, factor = 2, jitter = 0.3, random = Math.random } = options;

  return {
    delayFor(attempt) {
      const exponent = Math.max(0, attempt - 1);
      const raw = Math.min(maxMs, baseMs * factor ** exponent);
      const jitterSpan = raw * Math.min(Math.max(jitter, 0), 1);
      return Math.round(raw - jitterSpan * random());
    },
  };
}
