import { createEmitter } from '@/lib/emitter';

/**
 * Toast 总线：任何层（hooks / 组件 / 逻辑模块）都可调用 toast.xxx，
 * 由挂载在应用根部的 <Toaster/> 统一渲染。零依赖、可单测。
 */
export type ToastKind = 'info' | 'success' | 'error';

export interface ToastItem {
  id: number;
  kind: ToastKind;
  text: string;
  durationMs: number;
}

interface ToastBusEvents extends Record<string, unknown> {
  show: ToastItem;
}

const bus = createEmitter<ToastBusEvents>();
let sequence = 0;

const DEFAULT_DURATION: Record<ToastKind, number> = {
  info: 2400,
  success: 2400,
  error: 3600,
};

function show(kind: ToastKind, text: string, durationMs?: number): number {
  sequence += 1;
  const item: ToastItem = { id: sequence, kind, text, durationMs: durationMs ?? DEFAULT_DURATION[kind] };
  bus.emit('show', item);
  return item.id;
}

export const toast = {
  info: (text: string, durationMs?: number) => show('info', text, durationMs),
  success: (text: string, durationMs?: number) => show('success', text, durationMs),
  error: (text: string, durationMs?: number) => show('error', text, durationMs),
  show,
};

export function subscribeToToasts(listener: (item: ToastItem) => void): () => void {
  return bus.on('show', listener);
}
