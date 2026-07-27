import { DownloadOutlined, EllipsisOutlined, ExclamationCircleFilled, FileOutlined, ReloadOutlined } from '@ant-design/icons';
import { Avatar } from '@/components/ui/Avatar';
import { Menu, type MenuItem } from '@/components/ui/Menu';
import { Spinner } from '@/components/ui/Spinner';
import { formatTimeOfDay } from '@/lib/datetime';
import type { FileContent, ImageContent, SystemContent, TextContent } from '@/types/Message/Message';
import type { UiMessage } from '../model';

/**
 * 单条消息渲染：按 msgType 分发气泡；系统消息居中；撤回墓碑；
 * 失败态带重试；连续同发送者做视觉分组（隐藏头像与名字）。
 */
export interface MessageItemProps {
  message: UiMessage;
  isOwn: boolean;
  isGroup: boolean;
  showHeader: boolean;
  senderName: string;
  senderAvatar: string;
  menuItems: MenuItem[];
  onRetry?: () => void;
  onDiscardFailed?: () => void;
}

function formatBytes(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function TextBubble({ message, isOwn }: { message: UiMessage; isOwn: boolean }): React.JSX.Element {
  const content = message.content as TextContent;
  return (
    <p
      className={`min-w-0 break-words rounded-2xl px-3 py-2 text-sm leading-5 whitespace-pre-wrap ${
        isOwn ? 'rounded-br-md bg-[#0071e3] text-white' : 'rounded-bl-md bg-white text-[#1d1d1f] shadow-[0_1px_2px_rgba(0,0,0,0.06)]'
      }`}
    >
      {content.mentionAll ? <span className={isOwn ? 'font-semibold' : 'font-semibold text-[#0071e3]'}>@所有人 </span> : null}
      {content.text}
    </p>
  );
}

function ImageBubble({ message }: { message: UiMessage }): React.JSX.Element {
  const content = message.content as ImageContent;
  const ratio = content.width > 0 && content.height > 0 ? content.height / content.width : 0.75;
  const width = 220;
  return (
    <span className="relative block overflow-hidden rounded-2xl border border-black/[0.06] bg-[#f2f2f7]" style={{ width, height: Math.round(width * Math.min(ratio, 1.4)) }}>
      {content.url ? (
        <img src={content.thumbnailUrl || content.url} alt="图片消息" loading="lazy" className="size-full object-cover" />
      ) : (
        <span className="grid size-full place-items-center text-xs text-[#86868b]">图片不可用</span>
      )}
    </span>
  );
}

function FileBubble({ message, isOwn }: { message: UiMessage; isOwn: boolean }): React.JSX.Element {
  const content = message.content as FileContent;
  return (
    <span
      className={`flex w-56 items-center gap-3 rounded-2xl border px-3 py-2.5 ${
        isOwn ? 'border-transparent bg-[#0071e3] text-white' : 'border-black/[0.06] bg-white text-[#1d1d1f] shadow-[0_1px_2px_rgba(0,0,0,0.06)]'
      }`}
    >
      <span className={`grid size-9 shrink-0 place-items-center rounded-lg text-lg ${isOwn ? 'bg-white/20' : 'bg-[#eef3fb] text-[#0071e3]'}`}>
        <FileOutlined aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-medium">{content.name || '未命名文件'}</span>
        <span className={`block text-[11px] ${isOwn ? 'text-white/75' : 'text-[#86868b]'}`}>{formatBytes(content.size || 0)}</span>
      </span>
      {content.url ? (
        <a
          href={content.url}
          download={content.name}
          aria-label={`下载 ${content.name}`}
          className={`grid size-7 shrink-0 place-items-center rounded-full transition ${isOwn ? 'hover:bg-white/20' : 'hover:bg-black/[0.05]'}`}
        >
          <DownloadOutlined aria-hidden />
        </a>
      ) : null}
    </span>
  );
}

export function MessageItem({
  message,
  isOwn,
  isGroup,
  showHeader,
  senderName,
  senderAvatar,
  menuItems,
  onRetry,
  onDiscardFailed,
}: MessageItemProps): React.JSX.Element {
  // ---- 系统消息：居中弱化 ----
  if (message.msgType === 7) {
    const content = message.content as SystemContent;
    return (
      <li className="flex justify-center py-1">
        <span className="max-w-[85%] rounded-full bg-black/[0.045] px-3 py-1 text-center text-[11px] leading-4 text-[#86868b]">
          {content.detail || '系统消息'}
        </span>
      </li>
    );
  }

  // ---- 撤回墓碑 ----
  if (message.status === 2) {
    return (
      <li className="flex justify-center py-1">
        <span className="rounded-full bg-black/[0.045] px-3 py-1 text-[11px] text-[#86868b]">
          {isOwn ? '你' : senderName}撤回了一条消息
        </span>
      </li>
    );
  }

  const bubble =
    message.msgType === 2 ? (
      <ImageBubble message={message} />
    ) : message.msgType === 3 ? (
      <FileBubble message={message} isOwn={isOwn} />
    ) : (
      <TextBubble message={message} isOwn={isOwn} />
    );

  return (
    <li
      data-message-id={message.id}
      className={`group/message flex gap-2 ${isOwn ? 'flex-row-reverse' : 'flex-row'} ${showHeader ? 'mt-3' : 'mt-0.5'}`}
      style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 56px' } as React.CSSProperties}
    >
      <span className="w-9 shrink-0">
        {!isOwn && showHeader ? <Avatar name={senderName} src={senderAvatar || undefined} colorKey={message.senderId} /> : null}
      </span>

      <div className={`flex min-w-0 max-w-[min(78%,520px)] flex-col ${isOwn ? 'items-end' : 'items-start'}`}>
        {!isOwn && isGroup && showHeader ? (
          <span className="mb-1 px-1 text-[11px] text-[#86868b]">{senderName}</span>
        ) : null}

        {message.replyToId !== '0' && message.replyToPreview ? (
          <span className="mb-1 max-w-full truncate rounded-lg border-l-2 border-[#0071e3]/50 bg-black/[0.04] px-2 py-1 text-[11px] text-[#6e6e73]">
            {message.replyToPreview}
          </span>
        ) : null}

        <div className={`flex items-end gap-1.5 ${isOwn ? 'flex-row-reverse' : 'flex-row'}`}>
          {bubble}

          {/* 悬浮操作 */}
          {menuItems.length > 0 && message.sendState === 'sent' ? (
            <span className="opacity-0 transition-opacity group-hover/message:opacity-100">
              <Menu
                triggerLabel="消息操作"
                align={isOwn ? 'end' : 'start'}
                triggerClassName="grid size-6 place-items-center rounded-md text-[#86868b] hover:bg-black/[0.06] hover:text-[#1d1d1f]"
                trigger={<EllipsisOutlined aria-hidden />}
                items={menuItems}
              />
            </span>
          ) : null}

          {/* 发送状态 */}
          {message.sendState === 'sending' ? (
            <span className="mb-1 flex items-center gap-1 text-[10px] text-[#86868b]">
              {typeof message.progress === 'number' ? `${message.progress}%` : null}
              <Spinner size={12} />
            </span>
          ) : null}
          {message.sendState === 'failed' ? (
            <span className="mb-1 flex items-center gap-1">
              <ExclamationCircleFilled aria-hidden className="text-[#e5484d]" />
              {onRetry ? (
                <button
                  type="button"
                  aria-label="重新发送"
                  onClick={onRetry}
                  className="grid size-6 place-items-center rounded-md text-[#e5484d] hover:bg-[#fef1f1]"
                >
                  <ReloadOutlined aria-hidden />
                </button>
              ) : null}
              {onDiscardFailed ? (
                <button
                  type="button"
                  aria-label="放弃发送"
                  onClick={onDiscardFailed}
                  className="rounded-md px-1 text-[11px] text-[#86868b] hover:bg-black/[0.05]"
                >
                  放弃
                </button>
              ) : null}
            </span>
          ) : null}
        </div>

        <span className={`mt-1 flex items-center gap-1 px-1 text-[10px] text-[#a1a1a6] ${isOwn ? 'flex-row-reverse' : ''}`}>
          <time dateTime={new Date(message.createdAt).toISOString()}>{formatTimeOfDay(message.createdAt)}</time>
          {message.editedAt > 0 ? <span>已编辑</span> : null}
        </span>
      </div>
    </li>
  );
}
