import { ArrowLeftOutlined, MoreOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { EmptyState } from '@/components/ui/EmptyState';
import { PromptDialog } from '@/components/ui/PromptDialog';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { MessageComposer } from '@/modules/message/components/MessageComposer';
import { MessageList } from '@/modules/message/components/MessageList';
import { TypingIndicator } from '@/modules/message/components/TypingIndicator';
import { useAutoMarkRead, useMessageActions } from '@/modules/message/hooks';
import type { UiMessage } from '@/modules/message/model';
import { useTypingStore } from '@/stores/useTypingStore';
import { useWorkspaceStore } from '@/stores/useWorkspaceStore';
import type { TextContent } from '@/types/Message/Message';
import { useMarkRead, useMembersQuery } from '../hooks';
import type { UiConversation } from '../model';

/**
 * 聊天面板（中栏）：头部（对象信息 + typing）+ 消息列表 + 输入区。
 * 打开会话即触发自动已读（含 Page Visibility 联动）。
 */
export function ChatPanel({ conversation }: { conversation: UiConversation | null }): React.JSX.Element {
  const currentUser = useCurrentUser();
  const members = useMembersQuery(conversation?.id ?? null);
  const markRead = useMarkRead();
  const closeMobileChat = useWorkspaceStore((state) => state.closeMobileChat);
  const toggleDetailPanel = useWorkspaceStore((state) => state.toggleDetailPanel);
  const actions = useMessageActions(conversation?.id ?? null);
  const [editingMessage, setEditingMessage] = useState<UiMessage | null>(null);

  const typingMap = useTypingStore((state) => (conversation ? state.byConv[conversation.id] : undefined));
  const typingNames = useMemo(() => {
    if (!conversation || !typingMap) {
      return [];
    }
    const now = Date.now();
    const nameOf = new Map((members.data ?? []).map((member) => [member.userId, member.displayName]));
    return Object.entries(typingMap)
      .filter(([, expireAt]) => expireAt > now)
      .map(([userId]) => nameOf.get(userId) ?? '对方');
  }, [conversation, typingMap, members.data]);

  useAutoMarkRead(
    conversation ? { id: conversation.id, maxSeq: conversation.maxSeq, unreadCount: conversation.unreadCount } : null,
    markRead.mutate,
  );

  if (!conversation || !currentUser) {
    return (
      <section aria-label="聊天区" className="flex h-full flex-col bg-[#f5f5f7]">
        <EmptyState title="选择一个会话开始聊天" description="左侧选择会话，或点击 + 发起新会话" />
      </section>
    );
  }

  const myRole = (members.data ?? []).find((member) => member.userId === currentUser.id)?.role ?? 0;
  const composerDisabled = conversation.isMutedAll && myRole === 0;
  const subtitle =
    typingNames.length > 0 ? null : conversation.type === 'group' ? `${conversation.memberCount} 位成员` : '';

  return (
    <section aria-label="聊天区" className="flex h-full min-h-0 flex-col bg-[#f5f5f7]">
      <header className="flex h-14 shrink-0 items-center gap-3 border-b border-black/[0.06] bg-white/70 px-4 backdrop-blur-xl sm:px-5">
        <button
          type="button"
          aria-label="返回会话列表"
          onClick={closeMobileChat}
          className="grid size-8 place-items-center rounded-lg text-[#424245] transition hover:bg-black/[0.05] sm:hidden"
        >
          <ArrowLeftOutlined aria-hidden />
        </button>
        <Avatar name={conversation.name} src={conversation.avatar || undefined} colorKey={conversation.id} shape="rounded" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-[#1d1d1f]">{conversation.name}</p>
          <div className="mt-0.5 flex h-4 items-center text-xs text-[#86868b]">
            {typingNames.length > 0 ? <TypingIndicator names={typingNames} /> : subtitle}
          </div>
        </div>
        <button
          type="button"
          aria-label="会话详情"
          onClick={toggleDetailPanel}
          className="grid size-8 place-items-center rounded-lg text-[#424245] transition hover:bg-black/[0.05] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3]"
        >
          <MoreOutlined aria-hidden />
        </button>
      </header>

      <MessageList
        conversationId={conversation.id}
        isGroup={conversation.type === 'group'}
        currentUserId={currentUser.id}
        members={members.data ?? []}
        myRole={myRole}
        onEditMessage={setEditingMessage}
      />

      <MessageComposer
        conversationId={conversation.id}
        disabled={composerDisabled}
        disabledReason="全员禁言中"
      />

      <PromptDialog
        open={editingMessage !== null}
        title="编辑消息"
        initialValue={editingMessage ? ((editingMessage.content as TextContent).text ?? '') : ''}
        maxLength={2000}
        multiline
        pending={actions.edit.isPending}
        onClose={() => setEditingMessage(null)}
        onSubmit={(value) => {
          if (editingMessage && value.trim()) {
            actions.edit.mutate(
              { messageId: editingMessage.id, newContent: { text: value.trim() } },
              { onSuccess: () => setEditingMessage(null) },
            );
          }
        }}
      />
    </section>
  );
}
