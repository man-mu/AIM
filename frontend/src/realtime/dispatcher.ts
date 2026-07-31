import type { QueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/apis/queryKeys';
import { toast } from '@/components/ui/toast/toastBus';
import { systemScheduler, type Scheduler } from '@/lib/clock';
import { toInt64String } from '@/lib/ids';
import { applyIncomingPreview, setUnreadCount } from '@/modules/conversation/cache';
import type { UiConversation } from '@/modules/conversation/model';
import { appendIncoming, applyEdited, applyRecalled, type MessagesCache } from '@/modules/message/cache';
import { useTypingStore } from '@/stores/useTypingStore';
import type { MessageDTO } from '@/types/Message/Message';
import type { MessageNewData, RealtimeFrame } from './protocol';

/**
 * 实时事件派发器：下行帧 → Query 缓存 / Zustand 的最小增量更新。
 *
 * 原则：
 *  - 缓存操作全部走 modules/<域>/cache.ts 的纯函数（可单测）；
 *  - 缓存不存在（页面没打开过该数据）就什么都不做，交给下次 query 拉取；
 *  - 当前会话可见时新消息不产生未读，并自动上报已读。
 */
export interface DispatcherDeps {
  queryClient: QueryClient;
  getActiveConversationId(): string | null;
  isWindowVisible(): boolean;
  /** 自动已读上报（active 会话收到新消息时）。 */
  onAutoRead(conversationId: string, seq: number): void;
  scheduler?: Scheduler;
}

const TYPING_TTL_MS = 6_000;

export interface RealtimeDispatcher {
  handleFrame(frame: RealtimeFrame): void;
}

function messageDtoFromEvent(data: MessageNewData): MessageDTO {
  return {
    messageId: data.messageId,
    conversationId: data.convId,
    seq: data.seq,
    fromUserId: data.fromUserId,
    msgType: data.msgType,
    status: (data.status || 1) as MessageDTO['status'],
    content: data.content,
    replyToId: data.replyToId,
    replyToPreview: data.replyToPreview,
    editCount: 0,
    editedAt: 0,
    createdAt: data.createdAt,
  };
}

function previewFromEvent(data: MessageNewData): string {
  switch (data.msgType) {
    case 1:
      return (data.content as { text?: string }).text ?? '';
    case 2:
      return '[图片]';
    case 3:
      return '[文件]';
    case 7:
      return (data.content as { detail?: string }).detail ?? '[系统消息]';
    default:
      return '[消息]';
  }
}

export function createRealtimeDispatcher(deps: DispatcherDeps): RealtimeDispatcher {
  const scheduler = deps.scheduler ?? systemScheduler;
  const { queryClient } = deps;

  const handleMessageNew = (data: MessageNewData): void => {
    const convId = toInt64String(data.convId);
    const dto = messageDtoFromEvent(data);

    // 1. 消息分页缓存（若该会话已加载过）。
    queryClient.setQueryData<MessagesCache>(queryKeys.messages.pages(convId), (old) => appendIncoming(old, dto));

    // 2. 会话列表：预览/时间/未读。
    const isActive = deps.getActiveConversationId() === convId && deps.isWindowVisible();
    const list = queryClient.getQueryData<UiConversation[]>(queryKeys.conversations.list);
    const known = list?.some((conversation) => conversation.id === convId) ?? false;
    if (known) {
      queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
        applyIncomingPreview(old, {
          conversationId: convId,
          preview: previewFromEvent(data),
          at: data.createdAt,
          unreadCount: isActive ? 0 : data.unreadCount,
          maxSeq: data.seq,
        }),
      );
    } else {
      // 新会话（被拉群等）：整表刷新。
      void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.list });
    }

    // 3. 发送者显然不再输入。
    useTypingStore.getState().clearTyping(convId, toInt64String(data.fromUserId));

    // 4. 正在看这个会话：直接上报已读。
    if (isActive) {
      deps.onAutoRead(convId, data.seq);
    }
  };

  return {
    handleFrame(frame) {
      switch (frame.event) {
        case 'message.new':
          handleMessageNew(frame.data as MessageNewData);
          break;

        case 'message.recalled': {
          const data = frame.data as { messageId: string | number; convId: string | number };
          const convId = toInt64String(data.convId);
          queryClient.setQueryData<MessagesCache>(queryKeys.messages.pages(convId), (old) =>
            applyRecalled(old, toInt64String(data.messageId)),
          );
          break;
        }

        case 'message.edited': {
          const data = frame.data as {
            messageId: string | number;
            convId: string | number;
            newContent: MessageDTO['content'];
          };
          const convId = toInt64String(data.convId);
          queryClient.setQueryData<MessagesCache>(queryKeys.messages.pages(convId), (old) =>
            applyEdited(old, toInt64String(data.messageId), data.newContent, frame.timestamp),
          );
          break;
        }

        case 'typing': {
          const data = frame.data as { convId: string | number; userId: string | number };
          const convId = toInt64String(data.convId);
          const userId = toInt64String(data.userId);
          useTypingStore.getState().setTyping(convId, userId, scheduler.now() + TYPING_TTL_MS);
          scheduler.schedule(() => {
            const expireAt = useTypingStore.getState().byConv[convId]?.[userId];
            if (expireAt !== undefined && expireAt <= scheduler.now()) {
              useTypingStore.getState().clearTyping(convId, userId);
            }
          }, TYPING_TTL_MS + 100);
          break;
        }

        case 'typing.stop': {
          const data = frame.data as { convId: string | number; userId: string | number };
          useTypingStore.getState().clearTyping(toInt64String(data.convId), toInt64String(data.userId));
          break;
        }

        case 'unread_count': {
          const data = frame.data as { convId: string | number; count: number };
          queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
            setUnreadCount(old, toInt64String(data.convId), data.count),
          );
          break;
        }

        case 'conversation.updated': {
          const data = frame.data as { convId: string | number };
          const convId = toInt64String(data.convId);
          void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.members(convId) });
          void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.list });
          break;
        }

        case 'notification.new': {
          const data = frame.data as { title?: string };
          void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
          if (data.title) {
            toast.info(data.title);
          }
          break;
        }

        case 'read_sync':
        case 'read_receipt':
        case 'presence':
        case 'pong':
        default:
          // Phase B：已读回执与在线状态的 UI 呈现。
          break;
      }
    },
  };
}
