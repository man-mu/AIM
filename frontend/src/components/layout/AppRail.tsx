import { BellOutlined, MessageOutlined, TeamOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { NavLink, useNavigate } from 'react-router';
import { notificationApi } from '@/apis/notification';
import { queryKeys } from '@/apis/queryKeys';
import { Avatar } from '@/components/ui/Avatar';
import { UnreadBadge } from '@/components/ui/Badge';
import { Menu } from '@/components/ui/Menu';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useConversationsQuery } from '@/modules/conversation/hooks';
import { totalUnread } from '@/modules/conversation/model';
import { useRealtimeStore } from '@/stores/useRealtimeStore';

/**
 * 左侧导航栏（64px）：消息 / 联系人 / 通知 + 底部账户菜单。
 * 未读来源：会话列表聚合（免打扰不计）与通知未读数，皆为缓存派生。
 */
interface RailItemProps {
  to: string;
  label: string;
  icon: React.ReactNode;
  badge?: number;
}

function RailItem({ to, label, icon, badge = 0 }: RailItemProps): React.JSX.Element {
  return (
    <NavLink
      to={to}
      aria-label={label}
      className={({ isActive }) =>
        `relative grid size-11 place-items-center rounded-xl text-[19px] transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] ${
          isActive ? 'bg-[#0071e3]/10 text-[#0071e3]' : 'text-[#6e6e73] hover:bg-black/[0.05] hover:text-[#1d1d1f]'
        }`
      }
    >
      {icon}
      <UnreadBadge count={badge} className="absolute -top-0.5 -right-0.5" />
    </NavLink>
  );
}

const STATUS_HINT: Record<string, { color: string; text: string }> = {
  open: { color: '#30c552', text: '实时连接正常' },
  connecting: { color: '#f5a623', text: '连接中…' },
  reconnecting: { color: '#f5a623', text: '重连中…' },
  closed: { color: '#d2d2d7', text: '连接已断开' },
  idle: { color: '#d2d2d7', text: '未连接' },
};

export function AppRail({ onLogout, onOpenProfile }: { onLogout: () => void; onOpenProfile: () => void }): React.JSX.Element {
  const currentUser = useCurrentUser();
  const navigate = useNavigate();
  const conversations = useConversationsQuery();
  const chatUnread = totalUnread(conversations.data ?? []);
  const notificationUnread = useQuery({
    queryKey: queryKeys.notifications.unreadCount,
    queryFn: ({ signal }) => notificationApi.unreadCount({ signal }),
    staleTime: 30_000,
  });
  const status = useRealtimeStore((state) => state.status);
  const hint = STATUS_HINT[status] ?? STATUS_HINT.idle!;

  return (
    <nav
      aria-label="主导航"
      className="flex h-full w-16 shrink-0 flex-col items-center border-r border-black/[0.06] bg-[#f5f5f7]/80 py-4 backdrop-blur-xl"
    >
      <div className="mb-5 text-[15px] font-bold tracking-tight text-[#1d1d1f]">AIM</div>
      <div className="grid gap-1.5">
        <RailItem to="/home" label="消息" icon={<MessageOutlined aria-hidden />} badge={chatUnread} />
        <RailItem to="/contacts" label="联系人" icon={<TeamOutlined aria-hidden />} />
        <RailItem
          to="/notifications"
          label="通知"
          icon={<BellOutlined aria-hidden />}
          badge={notificationUnread.data?.count ?? 0}
        />
      </div>

      <div className="mt-auto grid justify-items-center gap-3">
        <span aria-label={hint.text} title={hint.text} className="size-2 rounded-full" style={{ backgroundColor: hint.color }} />
        <Menu
          triggerLabel="账户菜单"
          align="start"
          triggerClassName="rounded-full transition hover:opacity-85 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3]"
          trigger={
            <Avatar
              name={currentUser?.username ?? '我'}
              src={currentUser?.avatar || undefined}
              colorKey={currentUser?.id}
              size="md"
            />
          }
          items={[
            { key: 'profile', label: '个人资料', onSelect: onOpenProfile },
            { key: 'settings-home', label: '回到消息', onSelect: () => void navigate('/home') },
            { key: 'logout', label: '退出登录', danger: true, onSelect: onLogout },
          ]}
        />
      </div>
    </nav>
  );
}
