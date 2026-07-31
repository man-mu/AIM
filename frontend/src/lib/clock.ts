/**
 * 时间与调度的可注入抽象。
 *
 * 所有含定时行为的核心模块（重连退避、mock 实时事件、typing 过期）
 * 均依赖本接口而非直接调用 setTimeout —— 测试可注入虚拟调度器做到全确定性。
 */
export interface Scheduler {
  now(): number;
  /** 计划一次回调，返回取消函数。 */
  schedule(callback: () => void, delayMs: number): () => void;
}

export const systemScheduler: Scheduler = {
  now: () => Date.now(),
  schedule(callback, delayMs) {
    const handle = setTimeout(callback, delayMs);
    return () => clearTimeout(handle);
  },
};

/**
 * 手动推进的虚拟调度器（测试与 Storybook 式演示用）。
 */
export interface ManualScheduler extends Scheduler {
  /** 将虚拟时间向前推进并触发到期回调（按到期时间排序）。 */
  advance(ms: number): void;
  /** 尚未触发的任务数。 */
  pendingCount(): number;
}

export function createManualScheduler(startAt = 0): ManualScheduler {
  interface Job {
    at: number;
    seq: number;
    callback: () => void;
  }

  let current = startAt;
  let seq = 0;
  const jobs: Job[] = [];

  return {
    now: () => current,
    schedule(callback, delayMs) {
      const job: Job = { at: current + Math.max(0, delayMs), seq: (seq += 1), callback };
      jobs.push(job);
      return () => {
        const index = jobs.indexOf(job);
        if (index >= 0) {
          jobs.splice(index, 1);
        }
      };
    },
    advance(ms) {
      const target = current + ms;
      for (;;) {
        const due = jobs
          .filter((job) => job.at <= target)
          .sort((a, b) => a.at - b.at || a.seq - b.seq)[0];
        if (!due) {
          break;
        }
        jobs.splice(jobs.indexOf(due), 1);
        current = due.at;
        due.callback();
      }
      current = target;
    },
    pendingCount: () => jobs.length,
  };
}
