import { useEffect, useState } from 'react';
import { Dialog } from './Dialog';

/** 单输入框对话框（备注名 / 分组名 / 公告等轻编辑）。 */
export interface PromptDialogProps {
  open: boolean;
  title: string;
  placeholder?: string;
  initialValue?: string;
  maxLength?: number;
  multiline?: boolean;
  pending?: boolean;
  onSubmit: (value: string) => void;
  onClose: () => void;
}

export function PromptDialog({
  open,
  title,
  placeholder,
  initialValue = '',
  maxLength = 60,
  multiline = false,
  pending = false,
  onSubmit,
  onClose,
}: PromptDialogProps): React.JSX.Element {
  const [value, setValue] = useState(initialValue);

  useEffect(() => {
    if (open) {
      setValue(initialValue);
    }
  }, [open, initialValue]);

  const inputClass =
    'w-full rounded-lg border border-black/[0.14] bg-white px-3 py-2 text-sm text-[#1d1d1f] outline-none transition placeholder:text-[#86868b] focus:border-[#0071e3] focus:ring-[3px] focus:ring-[#0071e3]/15';

  return (
    <Dialog open={open} onClose={onClose} title={title} maxWidth={380}>
      {multiline ? (
        <textarea
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          maxLength={maxLength}
          rows={4}
          aria-label={title}
          className={`${inputClass} resize-none`}
        />
      ) : (
        <input
          name="prompt-value"
          autoComplete="off"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          maxLength={maxLength}
          aria-label={title}
          className={inputClass}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              onSubmit(value);
            }
          }}
        />
      )}
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
          onClick={() => onSubmit(value)}
          className="h-9 rounded-lg bg-[#0071e3] px-4 text-sm font-medium text-white transition hover:bg-[#0077ed] disabled:opacity-50"
        >
          {pending ? '保存中…' : '保存'}
        </button>
      </div>
    </Dialog>
  );
}
