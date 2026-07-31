import { DownOutlined } from '@ant-design/icons';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Spinner } from '@/components/ui/Spinner';
import { toast } from '@/components/ui/toast/toastBus';
import { formatDayDivider, needsTimeDivider } from '@/lib/datetime';
import type { MenuItem } from '@/components/ui/Menu';
import type { UiMember } from '@/modules/conversation/model';
import { RECALL_WINDOW_MS } from '@/mocks/db';
import type { TextContent } from '@/types/Message/Message';
import { useMessageActions, useMessagesQuery, useSendMessage, useVisibleMessages } from '../hooks';
import type { UiMessage } from '../model';
import { previewOfContent } from '../model';
import { usePendingMessagesStore } from '@/stores/usePendingMessagesStore';
import { useComposerStore } from '@/stores/useComposerStore';
import { MessageItem } from './MessageItem';

/**
 * 消息列表：
 *  - 游标分页：顶端哨兵进入视口（IntersectionObserver）即拉更早历史，
 *    并用 scrollHeight 差值补偿保持视口稳定；
 *  - 智能吸底：贴底时新消息自动滚动，非贴底时出现「回到最新」浮标；
 *  - 渲染成本：逐条 content-visibility:auto，离屏消息跳过渲染管线。
 */
export interface MessageListProps {
  conversationId: string;
  isGroup: boolean;
  currentUserId: string;
  members: UiMember[];
  myRole: 0 | 1 | 2;
  onEditMessage: (message: UiMessage) => void;
}

const NEAR_BOTTOM_PX = 80;

export function MessageList({
  conversationId,
  isGroup,
  currentUserId,
  members,
  myRole,
  onEditMessage,
}: MessageListProps): React.JSX.Element {
  const query = useMessagesQuery(conversationId);
  const messages = useVisibleMessages(conversationId);
  const actions = useMessageActions(conversationId);
  const send = useSendMessage(conversationId);
  const setReplyTarget = useComposerStore((state) => state.setReplyTarget);

  const scrollRef = useRef<HTMLDivElement>(null);
  const topSentinelRef = useRef<HTMLDivElement>(null);
  const stickToBottomRef = useRef(true);
  const prependCompensationRef = useRef<number | null>(null);
  const [showJumpToLatest, setShowJumpToLatest] = useState(false);

  const memberByUserId = useMemo(() => new Map(members.map((member) => [member.userId, member])), [members]);
  const senderNameOf = (message: UiMessage): string =>
    memberByUserId.get(message.senderId)?.displayName ?? '未知成员';

  const scrollToBottom = (behavior: ScrollBehavior = 'auto'): void => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTo({ top: el.scrollHeight, behavior });
    }
  };

  // 会话切换：重置吸底 + 立即到底。
  useLayoutEffect(() => {
    stickToBottomRef.current = true;
    setShowJumpToLatest(false);
    scrollToBottom();
  }, [conversationId]);

  // 消息集变化：向上补页做视口补偿；否则按吸底策略处理。
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) {
      return;
    }
    if (prependCompensationRef.current !== null) {
      el.scrollTop += el.scrollHeight - prependCompensationRef.current;
      prependCompensationRef.current = null;
      return;
    }
    if (stickToBottomRef.current) {
      scrollToBottom();
    } else {
      setShowJumpToLatest(true);
    }
  }, [messages.length]);

  // 顶端哨兵：进入视口即拉取更早历史。
  useEffect(() => {
    const sentinel = topSentinelRef.current;
    const el = scrollRef.current;
    if (!sentinel || !el || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry?.isIntersecting && query.hasNextPage && !query.isFetchingNextPage) {
          prependCompensationRef.current = el.scrollHeight;
          void query.fetchNextPage();
        }
      },
      { root: el, rootMargin: '120px 0px 0px 0px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [query, query.hasNextPage, query.isFetchingNextPage]);

  const handleScroll = (): void => {
    const el = scrollRef.current;
    if (!el) {
      return;
    }
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
    stickToBottomRef.current = nearBottom;
    if (nearBottom) {
      setShowJumpToLatest(false);
    }
  };

  const menuItemsFor = (message: UiMessage, isOwn: boolean): MenuItem[] => {
    const items: MenuItem[] = [];
    const withinWindow = Date.now() - message.createdAt <= RECALL_WINDOW_MS;
    const isManager = myRole === 1 || myRole === 2;

    items.push({
      key: 'reply',
      label: '引用回复',
      onSelect: () =>
        setReplyTarget(conversationId, {
          messageId: message.id,
          preview: previewOfContent(message.msgType, message.content),
          senderName: senderNameOf(message),
        }),
    });
    if (message.msgType === 1) {
      items.push({
        key: 'copy',
        label: '复制',
        onSelect: () => {
          const text = (message.content as TextContent).text ?? '';
          void navigator.clipboard?.writeText(text).then(
            () => toast.success('已复制'),
            () => toast.error('复制失败'),
          );
        },
      });
    }
    if (isOwn && message.msgType === 1 && withinWindow) {
      items.push({ key: 'edit', label: '编辑', onSelect: () => onEditMessage(message) });
    }
    if ((isOwn && withinWindow) || (!isOwn && isManager)) {
      items.push({ key: 'recall', label: '撤回', danger: true, onSelect: () => actions.recall.mutate(message.id) });
    }
    items.push({
      key: 'delete',
      label: '删除',
      danger: true,
      onSelect: () => actions.remove.mutate({ messageId: message.id, deleteForAll: false }),
    });
    return items;
  };

  const retryFailed = (message: UiMessage): void => {
    if (!message.clientMsgId) {
      return;
    }
    // 附件类失败：原始 File 已不在内存，无法原样重传 → 丢弃占位并提示重选。
    if (message.msgType !== 1) {
      usePendingMessagesStore.getState().remove(conversationId, message.clientMsgId);
      toast.error('附件发送失败，请重新选择文件发送');
      return;
    }
    void send({
      msgType: message.msgType,
      content: message.content,
      replyToId: message.replyToId === '0' ? undefined : message.replyToId,
      replyToPreview: message.replyToPreview,
      clientMsgId: message.clientMsgId,
    });
  };

  return (
    <div className="relative min-h-0 flex-1">
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="h-full overflow-y-auto overscroll-contain px-4 pt-3 pb-4 sm:px-6"
        aria-label="消息记录"
      >
        <div ref={topSentinelRef} aria-hidden className="h-px" />
        {query.isFetchingNextPage ? (
          <div className="flex justify-center py-2">
            <Spinner />
          </div>
        ) : null}
        {!query.hasNextPage && messages.length > 0 ? (
          <p className="py-2 text-center text-[11px] text-[#c7c7cc]">已加载全部历史</p>
        ) : null}

        <ol className="m-0 list-none p-0">
          {messages.map((message, index) => {
            const prev = index > 0 ? (messages[index - 1] as UiMessage) : null;
            const isOwn = message.senderId === currentUserId;
            const showDivider = needsTimeDivider(prev?.createdAt ?? null, message.createdAt);
            const showHeader =
              showDivider ||
              !prev ||
              prev.senderId !== message.senderId ||
              prev.msgType === 7 ||
              message.createdAt - prev.createdAt > 3 * 60 * 1000;
            const member = memberByUserId.get(message.senderId);

            return (
              <div key={message.id}>
                {showDivider ? (
                  <li className="my-3 flex justify-center">
                    <span className="rounded-full bg-black/[0.04] px-2.5 py-0.5 text-[10px] text-[#a1a1a6]">
                      {formatDayDivider(message.createdAt)}
                    </span>
                  </li>
                ) : null}
                <MessageItem
                  message={message}
                  isOwn={isOwn}
                  isGroup={isGroup}
                  showHeader={showHeader}
                  senderName={member?.displayName ?? '未知成员'}
                  senderAvatar={member?.avatar ?? ''}
                  menuItems={message.sendState === 'sent' ? menuItemsFor(message, isOwn) : []}
                  onRetry={message.sendState === 'failed' ? () => retryFailed(message) : undefined}
                  onDiscardFailed={
                    message.sendState === 'failed' && message.clientMsgId
                      ? () => usePendingMessagesStore.getState().remove(conversationId, message.clientMsgId as string)
                      : undefined
                  }
                />
              </div>
            );
          })}
        </ol>
      </div>

      {showJumpToLatest ? (
        <button
          type="button"
          onClick={() => {
            stickToBottomRef.current = true;
            setShowJumpToLatest(false);
            scrollToBottom('smooth');
          }}
          className="absolute right-4 bottom-4 flex items-center gap-1 rounded-full border border-black/[0.08] bg-white/95 px-3 py-1.5 text-xs font-medium text-[#0071e3] shadow-[0_6px_20px_rgba(0,0,0,0.12)] backdrop-blur transition hover:bg-white"
        >
          <DownOutlined aria-hidden />
          回到最新
        </button>
      ) : null}
    </div>
  );
}
