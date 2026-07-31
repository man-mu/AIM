import { EllipsisOutlined } from '@ant-design/icons';
import { Avatar } from '@/components/ui/Avatar';
import { Menu, type MenuItem } from '@/components/ui/Menu';
import { ROLE_LABELS, type UiMember } from '../model';

/**
 * 成员列表：角色标签 + 禁言标识；
 * 管理动作按权限矩阵渲染（群主 > 管理员 > 成员）。
 */
export interface MemberListProps {
  members: UiMember[];
  currentUserId: string;
  myRole: 0 | 1 | 2;
  onMute: (userId: string, durationSeconds: number) => void;
  onUnmute: (userId: string) => void;
  onKick: (userId: string) => void;
  onTransfer: (userId: string) => void;
}

export function MemberList({
  members,
  currentUserId,
  myRole,
  onMute,
  onUnmute,
  onKick,
  onTransfer,
}: MemberListProps): React.JSX.Element {
  const canManage = (target: UiMember): boolean => {
    if (target.userId === currentUserId) {
      return false;
    }
    if (myRole === 1) {
      return true;
    }
    // 管理员只能管理普通成员。
    return myRole === 2 && target.role === 0;
  };

  const menuFor = (member: UiMember): MenuItem[] => {
    const items: MenuItem[] = [];
    if (member.isMuted) {
      items.push({ key: 'unmute', label: '解除禁言', onSelect: () => onUnmute(member.userId) });
    } else {
      items.push(
        { key: 'mute-1h', label: '禁言 1 小时', onSelect: () => onMute(member.userId, 3600) },
        { key: 'mute-1d', label: '禁言 24 小时', onSelect: () => onMute(member.userId, 86400) },
        { key: 'mute-forever', label: '永久禁言', onSelect: () => onMute(member.userId, 0) },
      );
    }
    if (myRole === 1) {
      items.push({ key: 'transfer', label: '转让群主', onSelect: () => onTransfer(member.userId) });
    }
    items.push({ key: 'kick', label: '移出群聊', danger: true, onSelect: () => onKick(member.userId) });
    return items;
  };

  return (
    <ul className="m-0 grid list-none gap-0.5 p-0" role="list" aria-label="群成员">
      {members.map((member) => (
        <li
          key={member.userId}
          className="group/member flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition hover:bg-black/[0.04]"
          style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 44px' } as React.CSSProperties}
        >
          <Avatar name={member.displayName} src={member.avatar || undefined} colorKey={member.userId} size="sm" />
          <span className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <span className="min-w-0 truncate text-[13px] font-medium text-[#1d1d1f]">
                {member.displayName}
                {member.userId === currentUserId ? '（我）' : ''}
              </span>
              {member.role !== 0 ? (
                <span
                  className={`shrink-0 rounded px-1 py-px text-[10px] font-medium ${
                    member.role === 1 ? 'bg-[#f5a623]/15 text-[#b07408]' : 'bg-[#0071e3]/10 text-[#0071e3]'
                  }`}
                >
                  {ROLE_LABELS[member.role]}
                </span>
              ) : null}
              {member.isMuted ? (
                <span className="shrink-0 rounded bg-black/[0.06] px-1 py-px text-[10px] text-[#86868b]">已禁言</span>
              ) : null}
            </span>
          </span>
          {canManage(member) ? (
            <span className="opacity-0 transition-opacity group-hover/member:opacity-100">
              <Menu
                triggerLabel={`管理 ${member.displayName}`}
                triggerClassName="grid size-6 place-items-center rounded-md text-[#86868b] hover:bg-black/[0.08] hover:text-[#1d1d1f]"
                trigger={<EllipsisOutlined aria-hidden />}
                items={menuFor(member)}
              />
            </span>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
