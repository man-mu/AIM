import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { convApi } from '@/apis/conv';
import { queryKeys } from '@/apis/queryKeys';
import { useWorkspaceStore } from '@/stores/useWorkspaceStore';
import { upsertConversation } from './cache';
import { ChatPanel } from './components/ChatPanel';
import { ConversationList } from './components/ConversationList';
import { ConversationDetailPanel } from './components/ConversationDetailPanel';
import { CreateChatDialog } from './components/CreateChatDialog';
import { mapConversation, type UiConversation } from './model';
import { useConversation, useConversationsQuery } from './hooks';

/**
 * 消息工作台（/home/:conversationId?）：
 * 三栏布局，选中会话由 URL 驱动（刷新/分享可恢复）；
 * 深链会话不在列表缓存时按需拉取补位。
 */
export function ConversationWorkspace(): React.JSX.Element {
  const params = useParams<{ conversationId?: string }>();
  const conversationId = params.conversationId ?? null;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const listQuery = useConversationsQuery();
  const conversation = useConversation(conversationId);
  const isDetailPanelOpen = useWorkspaceStore((state) => state.isDetailPanelOpen);
  const isMobileChatOpen = useWorkspaceStore((state) => state.isMobileChatOpen);
  const isCreateDialogOpen = useWorkspaceStore((state) => state.isCreateDialogOpen);
  const setCreateDialogOpen = useWorkspaceStore((state) => state.setCreateDialogOpen);

  // 深链补位：列表已加载但找不到该会话 → 拉详情合入缓存；确认无权限/不存在 → 回列表。
  useEffect(() => {
    if (!conversationId || !listQuery.data || conversation) {
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const [dto, settings] = await Promise.all([
          convApi.getDetail(conversationId),
          convApi.getSettings(conversationId).catch(() => null),
        ]);
        if (!cancelled) {
          queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
            upsertConversation(old, mapConversation(dto, settings)),
          );
        }
      } catch {
        if (!cancelled) {
          void navigate('/home', { replace: true });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [conversation, conversationId, listQuery.data, navigate, queryClient]);

  return (
    <div className="flex h-full min-h-0">
      <div
        data-testid="conversation-sidebar"
        className={`${isMobileChatOpen ? 'hidden sm:flex' : 'flex'} w-full shrink-0 flex-col border-r border-black/[0.06] bg-white sm:w-72 lg:w-80`}
      >
        <ConversationList activeConversationId={conversationId} />
      </div>

      <div
        data-testid="chat-panel"
        className={`${isMobileChatOpen ? 'flex' : 'hidden sm:flex'} min-w-0 flex-1 flex-col`}
      >
        <ChatPanel conversation={conversation} />
      </div>

      {conversation && isDetailPanelOpen ? (
        <div data-testid="detail-panel" className="hidden w-72 shrink-0 border-l border-black/[0.06] lg:block">
          <ConversationDetailPanel conversation={conversation} />
        </div>
      ) : null}

      <CreateChatDialog open={isCreateDialogOpen} onClose={() => setCreateDialogOpen(false)} />
    </div>
  );
}
