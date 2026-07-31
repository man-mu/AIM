import { create } from 'zustand';

/**
 * 「正在输入」瞬时状态：conv → userId → 过期时间。
 * dispatcher 收到 typing 事件写入，typing.stop / 过期时清除。
 */
interface TypingState {
  byConv: Record<string, Record<string, number>>;
  setTyping: (conversationId: string, userId: string, expireAt: number) => void;
  clearTyping: (conversationId: string, userId: string) => void;
  typingUserIds: (conversationId: string, now: number) => string[];
}

export const useTypingStore = create<TypingState>((set, get) => ({
  byConv: {},
  setTyping: (conversationId, userId, expireAt) =>
    set((state) => ({
      byConv: {
        ...state.byConv,
        [conversationId]: { ...(state.byConv[conversationId] ?? {}), [userId]: expireAt },
      },
    })),
  clearTyping: (conversationId, userId) =>
    set((state) => {
      const conv = state.byConv[conversationId];
      if (!conv || !(userId in conv)) {
        return state;
      }
      const rest = { ...conv };
      delete rest[userId];
      return { byConv: { ...state.byConv, [conversationId]: rest } };
    }),
  typingUserIds: (conversationId, now) => {
    const conv = get().byConv[conversationId] ?? {};
    return Object.entries(conv)
      .filter(([, expireAt]) => expireAt > now)
      .map(([userId]) => userId);
  },
}));
