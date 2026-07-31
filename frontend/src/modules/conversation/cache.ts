import { sortConversations, type UiConversation } from './model';

/**
 * 会话列表缓存的纯更新函数：
 * 实时事件（message.new / unread_count / 已读操作）到达时,
 * dispatcher 用这些函数对 Query 缓存做最小化增量更新。
 * 全部纯函数,可在 Node 环境单测。
 */

export interface IncomingPreviewPatch {
  conversationId: string;
  preview: string;
  at: number;
  /** 服务端按人计算的未读数;当前会话在读时应传 0。 */
  unreadCount?: number;
  maxSeq?: number;
}

/** 新消息驱动的列表更新:改预览/时间/未读并重排。 */
export function applyIncomingPreview(
  list: UiConversation[] | undefined,
  patch: IncomingPreviewPatch,
): UiConversation[] | undefined {
  if (!list) {
    return list;
  }
  let found = false;
  const next = list.map((conversation) => {
    if (conversation.id !== patch.conversationId) {
      return conversation;
    }
    found = true;
    return {
      ...conversation,
      lastMessagePreview: patch.preview,
      lastActiveAt: patch.at,
      unreadCount: patch.unreadCount ?? conversation.unreadCount,
      maxSeq: patch.maxSeq ?? conversation.maxSeq,
    };
  });
  // 列表中不存在(新会话):交给上层触发 invalidate,这里保持原样。
  return found ? sortConversations(next) : list;
}

export function setUnreadCount(
  list: UiConversation[] | undefined,
  conversationId: string,
  unreadCount: number,
): UiConversation[] | undefined {
  if (!list) {
    return list;
  }
  return list.map((conversation) =>
    conversation.id === conversationId ? { ...conversation, unreadCount } : conversation,
  );
}

/** 本地已读:未读归零(乐观更新,markRead 请求同步进行)。 */
export function clearUnread(list: UiConversation[] | undefined, conversationId: string): UiConversation[] | undefined {
  return setUnreadCount(list, conversationId, 0);
}

export function patchConversation(
  list: UiConversation[] | undefined,
  conversationId: string,
  patch: Partial<UiConversation>,
): UiConversation[] | undefined {
  if (!list) {
    return list;
  }
  return sortConversations(
    list.map((conversation) => (conversation.id === conversationId ? { ...conversation, ...patch } : conversation)),
  );
}

export function upsertConversation(
  list: UiConversation[] | undefined,
  conversation: UiConversation,
): UiConversation[] {
  const base = list ?? [];
  const exists = base.some((item) => item.id === conversation.id);
  const next = exists ? base.map((item) => (item.id === conversation.id ? conversation : item)) : [...base, conversation];
  return sortConversations(next);
}
