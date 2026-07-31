import { create } from 'zustand';
import type { UiMessage } from '@/modules/message/model';

/**
 * 乐观发送队列：pending / failed 消息不进 Query 缓存，
 * 渲染层通过 flattenAscending(cache, pending) 合成最终列表。
 * 服务端确认（或实时回声）后从此处移除。
 */
interface PendingMessagesState {
  byConv: Record<string, UiMessage[]>;
  add: (message: UiMessage) => void;
  markFailed: (conversationId: string, clientMsgId: string) => void;
  markSending: (conversationId: string, clientMsgId: string) => void;
  setProgress: (conversationId: string, clientMsgId: string, progress: number) => void;
  remove: (conversationId: string, clientMsgId: string) => void;
  clearConversation: (conversationId: string) => void;
}

function updateList(
  byConv: Record<string, UiMessage[]>,
  conversationId: string,
  update: (list: UiMessage[]) => UiMessage[],
): Record<string, UiMessage[]> {
  const next = update(byConv[conversationId] ?? []);
  return { ...byConv, [conversationId]: next };
}

export const usePendingMessagesStore = create<PendingMessagesState>((set) => ({
  byConv: {},
  add: (message) =>
    set((state) => ({
      byConv: updateList(state.byConv, message.conversationId, (list) => [...list, message]),
    })),
  markFailed: (conversationId, clientMsgId) =>
    set((state) => ({
      byConv: updateList(state.byConv, conversationId, (list) =>
        list.map((item) => (item.clientMsgId === clientMsgId ? { ...item, sendState: 'failed' } : item)),
      ),
    })),
  markSending: (conversationId, clientMsgId) =>
    set((state) => ({
      byConv: updateList(state.byConv, conversationId, (list) =>
        list.map((item) => (item.clientMsgId === clientMsgId ? { ...item, sendState: 'sending' } : item)),
      ),
    })),
  setProgress: (conversationId, clientMsgId, progress) =>
    set((state) => ({
      byConv: updateList(state.byConv, conversationId, (list) =>
        list.map((item) => (item.clientMsgId === clientMsgId ? { ...item, progress } : item)),
      ),
    })),
  remove: (conversationId, clientMsgId) =>
    set((state) => ({
      byConv: updateList(state.byConv, conversationId, (list) =>
        list.filter((item) => item.clientMsgId !== clientMsgId),
      ),
    })),
  clearConversation: (conversationId) =>
    set((state) => ({ byConv: { ...state.byConv, [conversationId]: [] } })),
}));
