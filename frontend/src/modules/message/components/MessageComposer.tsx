import { CloseOutlined, PaperClipOutlined, SendOutlined } from '@ant-design/icons';
import { useEffect, useRef } from 'react';
import { useComposerStore } from '@/stores/useComposerStore';
import { useSendAttachment, useSendMessage } from '../hooks';

/**
 * 输入区：
 *  - 草稿按会话隔离并持久化（刷新/切换不丢）；
 *  - Enter 发送 / Shift+Enter 换行；textarea 自适应高度（≤6 行）；
 *  - 引用回复条；附件（图片/文件）走乐观上传管线；
 *  - 全员禁言时锁定输入（管理员除外）。
 */
export interface MessageComposerProps {
  conversationId: string;
  disabled?: boolean;
  disabledReason?: string;
}

export function MessageComposer({ conversationId, disabled = false, disabledReason }: MessageComposerProps): React.JSX.Element {
  const draft = useComposerStore((state) => state.drafts[conversationId] ?? '');
  const replyTarget = useComposerStore((state) => state.replyTargets[conversationId] ?? null);
  const setDraft = useComposerStore((state) => state.setDraft);
  const setReplyTarget = useComposerStore((state) => state.setReplyTarget);
  const send = useSendMessage(conversationId);
  const sendAttachment = useSendAttachment(conversationId);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 自适应高度（受控于内容，封顶 6 行）。
  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 6 * 24)}px`;
  }, [draft]);

  // 切换会话后聚焦输入框。
  useEffect(() => {
    textareaRef.current?.focus();
  }, [conversationId]);

  const canSend = draft.trim().length > 0 && !disabled;

  const submit = (): void => {
    const text = draft.trim();
    if (!text || disabled) {
      return;
    }
    void send({
      msgType: 1,
      content: { text },
      replyToId: replyTarget?.messageId,
      replyToPreview: replyTarget ? `${replyTarget.senderName}: ${replyTarget.preview}` : undefined,
    });
  };

  return (
    <footer className="border-t border-black/[0.06] bg-[#fbfbfd] px-4 py-3 sm:px-6">
      {replyTarget ? (
        <div className="mb-2 flex items-center gap-2 rounded-lg border-l-2 border-[#0071e3] bg-black/[0.04] px-3 py-1.5">
          <span className="min-w-0 flex-1 truncate text-xs text-[#6e6e73]">
            回复 {replyTarget.senderName}：{replyTarget.preview}
          </span>
          <button
            type="button"
            aria-label="取消引用"
            onClick={() => setReplyTarget(conversationId, null)}
            className="grid size-5 shrink-0 place-items-center rounded-full text-[#86868b] hover:bg-black/[0.06]"
          >
            <CloseOutlined aria-hidden style={{ fontSize: 10 }} />
          </button>
        </div>
      ) : null}

      <div
        className={`flex items-end gap-2 rounded-xl border bg-white px-3 py-2 transition ${
          disabled ? 'border-black/[0.08] bg-black/[0.03]' : 'border-black/[0.12] focus-within:border-[#0071e3] focus-within:ring-[3px] focus-within:ring-[#0071e3]/12'
        }`}
      >
        <button
          type="button"
          aria-label="发送附件"
          disabled={disabled}
          onClick={() => fileInputRef.current?.click()}
          className="grid size-8 shrink-0 place-items-center rounded-lg text-[#6e6e73] transition hover:bg-black/[0.05] hover:text-[#1d1d1f] disabled:cursor-not-allowed disabled:opacity-40"
        >
          <PaperClipOutlined aria-hidden />
        </button>
        <input
          ref={fileInputRef}
          type="file"
          aria-label="选择附件"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (file) {
              void sendAttachment(file);
            }
          }}
        />

        <textarea
          ref={textareaRef}
          aria-label="输入消息"
          value={draft}
          disabled={disabled}
          onChange={(event) => setDraft(conversationId, event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              submit();
            }
          }}
          rows={1}
          placeholder={disabled ? (disabledReason ?? '暂不可发言') : '输入消息，Enter 发送'}
          className="max-h-36 min-h-6 flex-1 resize-none bg-transparent text-sm leading-6 text-[#1d1d1f] outline-none placeholder:text-[#a1a1a6] disabled:cursor-not-allowed"
        />

        <button
          type="button"
          aria-label="发送消息"
          disabled={!canSend}
          onClick={submit}
          className="grid size-8 shrink-0 place-items-center rounded-lg bg-[#0071e3] text-white transition hover:bg-[#0077ed] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] disabled:cursor-not-allowed disabled:bg-[#d2d2d7]"
        >
          <SendOutlined aria-hidden />
        </button>
      </div>
    </footer>
  );
}
