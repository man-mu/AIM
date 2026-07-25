import { ArrowLeftOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useLocalConversation } from '../LocalConversationProvider';
import { MessageComposer } from './MessageComposer';
import { MessageList } from './MessageList';

export function ChatPanel(): React.JSX.Element {
  const { activeConversation, returnToConversationList } = useLocalConversation();
  const subtitle =
    activeConversation.type === 'direct'
      ? activeConversation.presence === 'online'
        ? '在线'
        : '离线'
      : `${activeConversation.memberCount} 位成员`;

  return (
    <section aria-label="聊天区" className="flex min-h-0 flex-1 flex-col bg-white">
      <header className="flex h-16 shrink-0 items-center gap-3 border-b border-black/[0.08] px-4 sm:px-6">
        <Tooltip title="返回会话列表">
          <button
            type="button"
            aria-label="返回会话列表"
            onClick={returnToConversationList}
            className="grid size-8 place-items-center rounded-md text-[#424245] transition hover:bg-black/[0.05] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] sm:hidden"
          >
            <ArrowLeftOutlined aria-hidden />
          </button>
        </Tooltip>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-[#1d1d1f]">{activeConversation.name}</p>
          <p className="mt-0.5 text-xs text-[#86868b]">{subtitle}</p>
        </div>
      </header>
      <MessageList />
      <MessageComposer />
    </section>
  );
}
