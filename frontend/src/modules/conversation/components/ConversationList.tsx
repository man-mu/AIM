import { PlusOutlined, PushpinFilled, SearchOutlined, StopOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { Avatar } from '@/components/ui/Avatar';
import { UnreadBadge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { Spinner } from '@/components/ui/Spinner';
import { formatConversationStamp } from '@/lib/datetime';
import { useWorkspaceStore } from '@/stores/useWorkspaceStore';
import { useConversationsQuery } from '../hooks';
import type { UiConversation } from '../model';

/**
 * 会话列表（左栏）：本地即时过滤 + 置顶分区 + 未读/免打扰标识。
 * 行级 content-visibility:auto——长列表离屏行跳过渲染。
 */
export function ConversationList({ activeConversationId }: { activeConversationId: string | null }): React.JSX.Element {
  const query = useConversationsQuery();
  const navigate = useNavigate();
  const openMobileChat = useWorkspaceStore((state) => state.openMobileChat);
  const setCreateDialogOpen = useWorkspaceStore((state) => state.setCreateDialogOpen);
  const [filter, setFilter] = useState('');

  const conversations = useMemo(() => {
    const list = query.data ?? [];
    const keyword = filter.trim().toLowerCase();
    if (!keyword) {
      return list;
    }
    return list.filter(
      (conversation) =>
        conversation.name.toLowerCase().includes(keyword) ||
        conversation.lastMessagePreview.toLowerCase().includes(keyword),
    );
  }, [query.data, filter]);

  const select = (conversation: UiConversation): void => {
    void navigate(`/home/${conversation.id}`);
    openMobileChat();
  };

  return (
    <section aria-label="会话列表" className="flex h-full min-h-0 flex-col">
      <header className="flex items-center gap-2 px-4 pt-4 pb-3">
        <div className="flex h-9 min-w-0 flex-1 items-center gap-2 rounded-lg bg-black/[0.05] px-3 transition focus-within:bg-white focus-within:ring-[3px] focus-within:ring-[#0071e3]/15">
          <SearchOutlined aria-hidden className="shrink-0 text-[#86868b]" />
          <input
            type="search"
            name="conversation-filter"
            autoComplete="off"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="搜索会话"
            aria-label="搜索会话"
            className="min-w-0 flex-1 bg-transparent text-sm text-[#1d1d1f] outline-none placeholder:text-[#a1a1a6]"
          />
        </div>
        <button
          type="button"
          aria-label="发起会话"
          onClick={() => setCreateDialogOpen(true)}
          className="grid size-9 shrink-0 place-items-center rounded-lg text-[#1d1d1f] transition hover:bg-black/[0.05] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3]"
        >
          <PlusOutlined aria-hidden />
        </button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
        {query.isLoading ? (
          <div className="flex justify-center py-10">
            <Spinner size={20} />
          </div>
        ) : conversations.length === 0 ? (
          <EmptyState
            title={filter ? '没有匹配的会话' : '暂无会话'}
            description={filter ? '换个关键词试试' : '点击右上角 + 发起第一个会话'}
          />
        ) : (
          <ul className="m-0 grid list-none gap-0.5 p-0" role="list">
            {conversations.map((conversation) => {
              const isActive = conversation.id === activeConversationId;
              return (
                <li key={conversation.id} style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 64px' } as React.CSSProperties}>
                  <button
                    type="button"
                    aria-current={isActive ? 'true' : undefined}
                    onClick={() => select(conversation)}
                    className={`grid w-full grid-cols-[2.75rem_minmax(0,1fr)_auto] items-center gap-2.5 rounded-xl px-2.5 py-2.5 text-left transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] ${
                      isActive ? 'bg-[#0071e3]/10' : 'hover:bg-black/[0.04]'
                    }`}
                  >
                    <Avatar name={conversation.name} src={conversation.avatar || undefined} colorKey={conversation.id} size="lg" shape="rounded" />
                    <span className="min-w-0">
                      <span className="flex items-center gap-1">
                        <span className="min-w-0 truncate text-sm font-medium text-[#1d1d1f]">{conversation.name}</span>
                        {conversation.isPinned ? <PushpinFilled aria-label="已置顶" className="shrink-0 text-[11px] text-[#a1a1a6]" /> : null}
                      </span>
                      <span className="mt-0.5 block truncate text-xs text-[#86868b]">
                        {conversation.lastMessagePreview || '暂无消息'}
                      </span>
                    </span>
                    <span className="flex h-full min-w-10 flex-col items-end justify-between py-0.5">
                      <time className="text-[10px] leading-none text-[#a1a1a6]" dateTime={new Date(conversation.lastActiveAt).toISOString()}>
                        {formatConversationStamp(conversation.lastActiveAt)}
                      </time>
                      <span className="flex items-center gap-1">
                        {conversation.isDnd ? <StopOutlined aria-label="免打扰" className="text-[11px] text-[#c7c7cc]" /> : null}
                        <UnreadBadge count={conversation.unreadCount} muted={conversation.isDnd} />
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
