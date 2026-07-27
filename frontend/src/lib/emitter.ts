/**
 * 类型安全的轻量事件发射器（零依赖）。
 *
 * 用于跨层解耦：API 层发出 `auth:expired`、实时层派发下行事件、Toast 总线等。
 * 单个监听器抛错不会影响其他监听器（隔离 + console.error 上报）。
 */
export type Listener<T> = (payload: T) => void;

export interface Emitter<Events extends Record<string, unknown>> {
  /** 订阅事件，返回取消订阅函数。 */
  on<K extends keyof Events>(type: K, listener: Listener<Events[K]>): () => void;
  /** 只触发一次的订阅。 */
  once<K extends keyof Events>(type: K, listener: Listener<Events[K]>): () => void;
  off<K extends keyof Events>(type: K, listener: Listener<Events[K]>): void;
  emit<K extends keyof Events>(type: K, payload: Events[K]): void;
  /** 当前监听器数量（测试与诊断用）。 */
  listenerCount(type: keyof Events): number;
  /** 移除全部监听器。 */
  clear(): void;
}

export function createEmitter<Events extends Record<string, unknown>>(): Emitter<Events> {
  const listeners = new Map<keyof Events, Set<Listener<never>>>();

  const getBucket = (type: keyof Events): Set<Listener<never>> => {
    let bucket = listeners.get(type);
    if (!bucket) {
      bucket = new Set();
      listeners.set(type, bucket);
    }
    return bucket;
  };

  const off = <K extends keyof Events>(type: K, listener: Listener<Events[K]>): void => {
    listeners.get(type)?.delete(listener as Listener<never>);
  };

  const on = <K extends keyof Events>(type: K, listener: Listener<Events[K]>): (() => void) => {
    getBucket(type).add(listener as Listener<never>);
    return () => off(type, listener);
  };

  return {
    on,
    off,
    once(type, listener) {
      const dispose = on(type, (payload) => {
        dispose();
        listener(payload);
      });
      return dispose;
    },
    emit(type, payload) {
      const bucket = listeners.get(type);
      if (!bucket || bucket.size === 0) {
        return;
      }

      // 快照遍历：允许监听器在回调中安全地取消订阅自己或他人。
      for (const listener of [...bucket]) {
        try {
          (listener as Listener<Events[typeof type]>)(payload);
        } catch (error) {
          console.error(`[emitter] listener for "${String(type)}" threw`, error);
        }
      }
    },
    listenerCount(type) {
      return listeners.get(type)?.size ?? 0;
    },
    clear() {
      listeners.clear();
    },
  };
}
