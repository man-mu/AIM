import { TeamOutlined, UserOutlined } from '@ant-design/icons';
import { useLocalConversation } from '../LocalConversationProvider';

export function ConversationDetailPanel(): React.JSX.Element {
  const { activeConversation } = useLocalConversation();
  const isGroup = activeConversation.type === 'group';
  const status = activeConversation.presence === 'online' ? '在线' : '离线';

  return (
    <section aria-label="会话详情" className="space-y-6">
      <div className="border-b border-black/[0.08] pb-5">
        <span className="grid size-12 place-items-center rounded-full bg-[#dbe8f7] text-lg font-semibold text-[#24527a]" aria-hidden>
          {(activeConversation.avatar || activeConversation.name).trim().slice(0, 1).toUpperCase()}
        </span>
        <h2 className="mt-3 text-base font-semibold text-[#1d1d1f]">{activeConversation.name}</h2>
        <p className="mt-1 flex items-center gap-2 text-sm text-[#6e6e73]">
          {isGroup ? <TeamOutlined aria-hidden /> : <UserOutlined aria-hidden />}
          {isGroup ? `${activeConversation.memberCount} 位成员` : status}
        </p>
      </div>
      {isGroup ? (
        <div>
          <h3 className="text-xs font-semibold text-[#86868b]">公告</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-[#424245]">
            {activeConversation.announcement || '暂无公告'}
          </p>
        </div>
      ) : null}
    </section>
  );
}
