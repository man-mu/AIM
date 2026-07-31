import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

/**
 * 基于原生 <dialog> 的模态框：
 * - showModal() 自带焦点圈定、Esc 关闭、::backdrop、顶层渲染（无需 z-index 战争）；
 * - 点击背板关闭（判定点击目标是 dialog 自身）；
 * - 关闭动画后再真正 close（配合 index.css 的 aim-dialog 类）。
 */
export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  /** 宽度上限（默认 420px）。 */
  maxWidth?: number;
}

export function Dialog({ open, onClose, title, children, maxWidth = 420 }: DialogProps): React.JSX.Element {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      // jsdom 无 showModal 实现：降级为 open 属性。
      if (typeof dialog.showModal === 'function') {
        dialog.showModal();
      } else {
        dialog.open = true;
      }
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === dialogRef.current) {
          onClose();
        }
      }}
      className="aim-dialog m-auto w-[calc(100vw-32px)] rounded-2xl border border-black/[0.08] bg-white p-0 text-[#1d1d1f] shadow-[0_24px_80px_rgba(0,0,0,0.18)] backdrop:bg-black/30 backdrop:backdrop-blur-[2px]"
      style={{ maxWidth }}
    >
      {title ? (
        <header className="flex items-center justify-between border-b border-black/[0.06] px-5 py-4">
          <h2 className="text-[15px] font-semibold">{title}</h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="grid size-7 place-items-center rounded-full text-[#86868b] transition hover:bg-black/[0.06] hover:text-[#1d1d1f]"
          >
            ×
          </button>
        </header>
      ) : null}
      <div className="p-5">{children}</div>
    </dialog>
  );
}
