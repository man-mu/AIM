import { useEffect, useRef, useState } from 'react';
import { subscribeToToasts, type ToastItem } from './toastBus';

const KIND_ICON: Record<ToastItem['kind'], string> = {
  info: '',
  success: '✓',
  error: '!',
};

const KIND_ICON_CLASS: Record<ToastItem['kind'], string> = {
  info: 'hidden',
  success: 'grid size-4 place-items-center rounded-full bg-[#30c552] text-[10px] font-bold text-white',
  error: 'grid size-4 place-items-center rounded-full bg-[#ff453a] text-[10px] font-bold text-white',
};

/**
 * 顶部居中的 Toast 栈（Apple 风格毛玻璃胶囊）。
 * - aria-live="polite"：读屏器播报，不打断当前操作；
 * - hover 暂停自动消失；
 * - prefers-reduced-motion 下关闭动画（见 index.css）。
 */
export function Toaster(): React.JSX.Element {
  const [items, setItems] = useState<ToastItem[]>([]);
  const timersRef = useRef(new Map<number, ReturnType<typeof setTimeout>>());
  const hoverRef = useRef(false);

  useEffect(() => {
    const dismiss = (id: number): void => {
      timersRef.current.delete(id);
      setItems((current) => current.filter((item) => item.id !== id));
    };

    const scheduleDismiss = (item: ToastItem): void => {
      const handle = setTimeout(() => {
        if (hoverRef.current) {
          // 悬停中：延后重试。
          scheduleDismiss({ ...item, durationMs: 800 });
          return;
        }
        dismiss(item.id);
      }, item.durationMs);
      timersRef.current.set(item.id, handle);
    };

    const unsubscribe = subscribeToToasts((item) => {
      setItems((current) => [...current.slice(-3), item]);
      scheduleDismiss(item);
    });

    const timers = timersRef.current;
    return () => {
      unsubscribe();
      for (const handle of timers.values()) {
        clearTimeout(handle);
      }
      timers.clear();
    };
  }, []);

  const removeNow = (id: number): void => {
    const handle = timersRef.current.get(id);
    if (handle) {
      clearTimeout(handle);
      timersRef.current.delete(id);
    }
    setItems((current) => current.filter((item) => item.id !== id));
  };

  return (
    <div
      aria-live="polite"
      role="status"
      className="pointer-events-none fixed inset-x-0 top-4 z-[1000] flex flex-col items-center gap-2"
      onMouseEnter={() => {
        hoverRef.current = true;
      }}
      onMouseLeave={() => {
        hoverRef.current = false;
      }}
    >
      {items.map((item) => (
        <div
          key={item.id}
          className="aim-toast-enter pointer-events-auto flex max-w-[min(92vw,420px)] items-center gap-2 rounded-full border border-black/[0.08] bg-white/90 px-4 py-2.5 text-[13px] font-medium text-[#1d1d1f] shadow-[0_8px_30px_rgba(0,0,0,0.12)] backdrop-blur-xl"
        >
          <span aria-hidden className={KIND_ICON_CLASS[item.kind]}>
            {KIND_ICON[item.kind]}
          </span>
          <span className="min-w-0 break-words">{item.text}</span>
          <button
            type="button"
            aria-label="关闭提示"
            onClick={() => removeNow(item.id)}
            className="ml-1 grid size-5 shrink-0 place-items-center rounded-full text-[#86868b] transition hover:bg-black/[0.06] hover:text-[#1d1d1f]"
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
