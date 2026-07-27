import { Dialog } from './Dialog';

/** 二次确认对话框（危险操作统一入口）。 */
export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description?: string;
  confirmText?: string;
  danger?: boolean;
  pending?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmText = '确定',
  danger = false,
  pending = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps): React.JSX.Element {
  return (
    <Dialog open={open} onClose={onClose} title={title} maxWidth={360}>
      {description ? <p className="text-sm leading-6 text-[#424245]">{description}</p> : null}
      <div className="mt-5 flex justify-end gap-2">
        <button
          type="button"
          onClick={onClose}
          className="h-9 rounded-lg border border-black/[0.12] px-4 text-sm font-medium text-[#424245] transition hover:border-black/[0.25]"
        >
          取消
        </button>
        <button
          type="button"
          disabled={pending}
          onClick={onConfirm}
          className={`h-9 rounded-lg px-4 text-sm font-medium text-white transition disabled:opacity-50 ${
            danger ? 'bg-[#e5484d] hover:bg-[#d93d42]' : 'bg-[#0071e3] hover:bg-[#0077ed]'
          }`}
        >
          {pending ? '处理中…' : confirmText}
        </button>
      </div>
    </Dialog>
  );
}
