import { MessageOutlined } from '@ant-design/icons';
import { useLocalConversation } from '../LocalConversationProvider';
import type { ConversationSummary } from '../types';

function formatConversationTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(timestamp);
}

function conversationInitial(conversation: ConversationSummary): string {
  return (conversation.avatar || conversation.name).trim().slice(0, 1).toUpperCase();
}

export function ConversationList(): React.JSX.Element {
  const { activeConversationId, conversations, selectConversation } = useLocalConversation();

  return (
    <section aria-label="会话列表" className="flex min-h-0 flex-col">
      <div className="flex items-center justify-between px-1 pb-3">
        <h2 className="text-sm font-semibold text-[#1d1d1f]">消息</h2>
        <MessageOutlined aria-hidden className="text-[#86868b]" />
      </div>
      <ul className="grid gap-1" role="list">
        {conversations.map((conversation) => {
          const isActive = conversation.id === activeConversationId;

          return (
            <li key={conversation.id}>
              <button
                type="button"
                aria-current={isActive ? 'true' : undefined}
                onClick={() => selectConversation(conversation.id)}
                className={
                  isActive
                    ? 'grid w-full grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-2 rounded-lg bg-[#e8f2ff] px-2 py-2 text-left transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3]'
                    : 'grid w-full grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-2 rounded-lg px-2 py-2 text-left transition hover:bg-black/[0.04] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3]'
                }
              >
                <span className="grid size-9 place-items-center rounded-full bg-[#dbe8f7] text-sm font-semibold text-[#24527a]" aria-hidden>
                  {conversationInitial(conversation)}
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-[#1d1d1f]">{conversation.name}</span>
                  <span className="mt-0.5 block truncate text-xs text-[#6e6e73]">
                    {conversation.lastMessagePreview}
                  </span>
                </span>
                <span className="flex min-w-9 flex-col items-end gap-1">
                  <time className="text-[11px] leading-none text-[#86868b]" dateTime={new Date(conversation.lastMessageAt).toISOString()}>
                    {formatConversationTime(conversation.lastMessageAt)}
                  </time>
                  {conversation.unreadCount > 0 ? (
                    <span
                      aria-label={`未读 ${conversation.unreadCount} 条`}
                      className="grid min-w-4 place-items-center rounded-full bg-[#0071e3] px-1 text-[10px] font-semibold leading-4 text-white"
                    >
                      {conversation.unreadCount}
                    </span>
                  ) : null}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
