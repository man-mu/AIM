import { create } from 'zustand';
import { createJsonKV } from '@/lib/storageKV';

/**
 * 输入框草稿（按会话隔离）+ 引用回复状态。
 * 草稿文本持久化到 localStorage（防抖 500ms），刷新不丢。
 */
export interface ReplyTarget {
  messageId: string;
  preview: string;
  senderName: string;
}

interface ComposerState {
  drafts: Record<string, string>;
  replyTargets: Record<string, ReplyTarget | null>;
  setDraft: (conversationId: string, text: string) => void;
  setReplyTarget: (conversationId: string, target: ReplyTarget | null) => void;
  clearComposer: (conversationId: string) => void;
}

const kv = createJsonKV('aim-ui', 1);
const DRAFTS_KEY = 'composer-drafts';

let persistTimer: ReturnType<typeof setTimeout> | null = null;

function schedulePersist(drafts: Record<string, string>): void {
  if (persistTimer) {
    clearTimeout(persistTimer);
  }
  persistTimer = setTimeout(() => {
    // 只保留非空草稿，避免存储无限膨胀。
    const compact = Object.fromEntries(Object.entries(drafts).filter(([, text]) => text.trim() !== ''));
    kv.write(DRAFTS_KEY, compact);
  }, 500);
}

export const useComposerStore = create<ComposerState>((set) => ({
  drafts: kv.read<Record<string, string>>(DRAFTS_KEY, {}),
  replyTargets: {},
  setDraft: (conversationId, text) =>
    set((state) => {
      const drafts = { ...state.drafts, [conversationId]: text };
      schedulePersist(drafts);
      return { drafts };
    }),
  setReplyTarget: (conversationId, target) =>
    set((state) => ({ replyTargets: { ...state.replyTargets, [conversationId]: target } })),
  clearComposer: (conversationId) =>
    set((state) => {
      const drafts = { ...state.drafts, [conversationId]: '' };
      schedulePersist(drafts);
      return {
        drafts,
        replyTargets: { ...state.replyTargets, [conversationId]: null },
      };
    }),
}));
