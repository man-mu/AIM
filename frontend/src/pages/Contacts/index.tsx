import { EllipsisOutlined, SearchOutlined, UserAddOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Avatar } from '@/components/ui/Avatar';
import { UnreadBadge } from '@/components/ui/Badge';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { Dialog } from '@/components/ui/Dialog';
import { EmptyState } from '@/components/ui/EmptyState';
import { Menu, type MenuItem } from '@/components/ui/Menu';
import { PromptDialog } from '@/components/ui/PromptDialog';
import { Spinner } from '@/components/ui/Spinner';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useCreateConversation } from '@/modules/conversation/hooks';
import { useUserSearch } from '@/modules/user/hooks';
import {
  useBlacklistQuery,
  useContactActions,
  useFriendGroupsQuery,
  useFriendsQuery,
  usePendingRequestsQuery,
  useSentRequestsQuery,
  type UiFriend,
} from '@/modules/contacts/hooks';

/**
 * 联系人页：好友（按分组）/ 新的朋友（申请处理）/ 黑名单。
 * tab 状态入 URL search（?tab=requests），通知页可深链跳转。
 */
type TabKey = 'friends' | 'requests' | 'blacklist';

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'friends', label: '好友' },
  { key: 'requests', label: '新的朋友' },
  { key: 'blacklist', label: '黑名单' },
];

export default function Contacts(): React.JSX.Element {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const tab: TabKey = tabParam === 'requests' || tabParam === 'blacklist' ? tabParam : 'friends';
  const pending = usePendingRequestsQuery();

  return (
    <div className="flex h-full min-h-0">
      <nav aria-label="联系人分类" className="w-52 shrink-0 border-r border-black/[0.06] bg-white p-3">
        <h1 className="px-2 py-2 text-base font-semibold text-[#1d1d1f]">联系人</h1>
        <ul className="m-0 grid list-none gap-0.5 p-0">
          {TABS.map((item) => (
            <li key={item.key}>
              <button
                type="button"
                aria-current={tab === item.key ? 'true' : undefined}
                onClick={() => setSearchParams({ tab: item.key })}
                className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition ${
                  tab === item.key ? 'bg-[#0071e3]/10 font-medium text-[#0071e3]' : 'text-[#424245] hover:bg-black/[0.04]'
                }`}
              >
                {item.label}
                {item.key === 'requests' ? <UnreadBadge count={pending.data?.length ?? 0} /> : null}
              </button>
            </li>
          ))}
        </ul>
      </nav>

      <main className="min-w-0 flex-1 overflow-y-auto bg-[#f5f5f7]">
        {tab === 'friends' ? <FriendsTab /> : tab === 'requests' ? <RequestsTab /> : <BlacklistTab />}
      </main>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 好友
// ---------------------------------------------------------------------------
function FriendsTab(): React.JSX.Element {
  const friends = useFriendsQuery();
  const groups = useFriendGroupsQuery();
  const actions = useContactActions();
  const createConversation = useCreateConversation();
  const [filter, setFilter] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [remarkTarget, setRemarkTarget] = useState<UiFriend | null>(null);
  const [confirm, setConfirm] = useState<{ title: string; description: string; danger: boolean; run: () => void } | null>(null);
  const [groupPrompt, setGroupPrompt] = useState<{ mode: 'create' } | { mode: 'rename'; groupId: string; name: string } | null>(null);

  const sections = useMemo(() => {
    const keyword = filter.trim().toLowerCase();
    const list = (friends.data ?? []).filter(
      (friend) => !keyword || friend.displayName.toLowerCase().includes(keyword) || friend.username.toLowerCase().includes(keyword),
    );
    const byGroup = new Map<string, UiFriend[]>();
    for (const friend of list) {
      const bucket = byGroup.get(friend.groupId);
      if (bucket) {
        bucket.push(friend);
      } else {
        byGroup.set(friend.groupId, [friend]);
      }
    }
    return (groups.data ?? [])
      .map((group) => ({ group, friends: byGroup.get(group.groupId) ?? [] }))
      .filter((section) => section.friends.length > 0 || !keyword);
  }, [friends.data, groups.data, filter]);

  const friendMenu = (friend: UiFriend): MenuItem[] => [
    {
      key: 'chat',
      label: '发消息',
      onSelect: () => createConversation.mutate({ type: 1, peerUserId: friend.userId }),
    },
    { key: 'remark', label: '设置备注', onSelect: () => setRemarkTarget(friend) },
    ...(groups.data ?? [])
      .filter((group) => group.groupId !== friend.groupId)
      .map((group) => ({
        key: `move-${group.groupId}`,
        label: `移至「${group.name}」`,
        onSelect: () => actions.moveToGroup.mutate({ friendId: friend.userId, groupId: group.groupId }),
      })),
    {
      key: 'delete',
      label: '删除好友',
      danger: true,
      onSelect: () =>
        setConfirm({
          title: '删除好友',
          description: `确定删除好友 ${friend.displayName} 吗？`,
          danger: true,
          run: () => actions.removeFriend.mutate(friend.userId),
        }),
    },
    {
      key: 'block',
      label: '加入黑名单',
      danger: true,
      onSelect: () =>
        setConfirm({
          title: '加入黑名单',
          description: `拉黑后将解除好友关系，且对方无法向你发起申请。确定拉黑 ${friend.displayName} 吗？`,
          danger: true,
          run: () => actions.block.mutate(friend.userId),
        }),
    },
  ];

  return (
    <div className="mx-auto max-w-2xl p-5">
      <div className="mb-4 flex items-center gap-2">
        <div className="flex h-9 min-w-0 flex-1 items-center gap-2 rounded-lg bg-white px-3 shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition focus-within:ring-[3px] focus-within:ring-[#0071e3]/15">
          <SearchOutlined aria-hidden className="text-[#86868b]" />
          <input
            type="search"
            name="friend-filter"
            autoComplete="off"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="搜索好友"
            aria-label="搜索好友"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-[#a1a1a6]"
          />
        </div>
        <button
          type="button"
          onClick={() => setAddOpen(true)}
          className="flex h-9 items-center gap-1.5 rounded-lg bg-[#0071e3] px-3 text-sm font-medium text-white transition hover:bg-[#0077ed]"
        >
          <UserAddOutlined aria-hidden />
          添加好友
        </button>
        <Menu
          triggerLabel="分组管理"
          triggerClassName="grid size-9 place-items-center rounded-lg bg-white text-[#424245] shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition hover:bg-black/[0.03]"
          trigger={<EllipsisOutlined aria-hidden />}
          items={[
            { key: 'create-group', label: '新建分组', onSelect: () => setGroupPrompt({ mode: 'create' }) },
            ...(groups.data ?? [])
              .filter((group) => group.groupId !== '0')
              .flatMap((group) => [
                {
                  key: `rename-${group.groupId}`,
                  label: `重命名「${group.name}」`,
                  onSelect: () => setGroupPrompt({ mode: 'rename', groupId: group.groupId, name: group.name }),
                },
                {
                  key: `delete-${group.groupId}`,
                  label: `删除「${group.name}」`,
                  danger: true,
                  onSelect: () =>
                    setConfirm({
                      title: '删除分组',
                      description: `删除后组内好友将回到默认分组。确定删除「${group.name}」吗？`,
                      danger: true,
                      run: () => actions.deleteGroup.mutate(group.groupId),
                    }),
                },
              ]),
          ]}
        />
      </div>

      {friends.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size={20} />
        </div>
      ) : (friends.data ?? []).length === 0 ? (
        <EmptyState title="还没有好友" description="点击右上角「添加好友」开始建立联系" />
      ) : (
        sections.map(({ group, friends: groupFriends }) => (
          <section key={group.groupId} className="mb-4">
            <h2 className="px-1 pb-1.5 text-xs font-semibold text-[#86868b]">
              {group.name}（{groupFriends.length}）
            </h2>
            <ul className="m-0 grid list-none gap-1 p-0">
              {groupFriends.map((friend) => (
                <li
                  key={friend.userId}
                  className="group/friend flex items-center gap-3 rounded-xl bg-white px-3 py-2.5 shadow-[0_1px_2px_rgba(0,0,0,0.04)]"
                >
                  <span className="relative">
                    <Avatar name={friend.displayName} src={friend.avatar || undefined} colorKey={friend.userId} />
                    {friend.online ? (
                      <span aria-label="在线" className="absolute -right-0.5 -bottom-0.5 size-2.5 rounded-full border-2 border-white bg-[#30c552]" />
                    ) : null}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-[#1d1d1f]">{friend.displayName}</span>
                    {friend.remark ? <span className="block truncate text-[11px] text-[#86868b]">用户名：{friend.username}</span> : null}
                  </span>
                  <span className="opacity-0 transition-opacity group-hover/friend:opacity-100">
                    <Menu
                      triggerLabel={`管理好友 ${friend.displayName}`}
                      triggerClassName="grid size-7 place-items-center rounded-md text-[#86868b] hover:bg-black/[0.06] hover:text-[#1d1d1f]"
                      trigger={<EllipsisOutlined aria-hidden />}
                      items={friendMenu(friend)}
                    />
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      <AddFriendDialog open={addOpen} onClose={() => setAddOpen(false)} />
      <PromptDialog
        open={remarkTarget !== null}
        title={`设置备注（${remarkTarget?.username ?? ''}）`}
        initialValue={remarkTarget?.remark ?? ''}
        placeholder="备注名"
        maxLength={20}
        onClose={() => setRemarkTarget(null)}
        onSubmit={(value) => {
          if (remarkTarget) {
            actions.setRemark.mutate({ friendId: remarkTarget.userId, remark: value.trim() });
          }
          setRemarkTarget(null);
        }}
      />
      <PromptDialog
        open={groupPrompt !== null}
        title={groupPrompt?.mode === 'rename' ? '重命名分组' : '新建分组'}
        initialValue={groupPrompt?.mode === 'rename' ? groupPrompt.name : ''}
        placeholder="分组名称"
        maxLength={16}
        onClose={() => setGroupPrompt(null)}
        onSubmit={(value) => {
          const name = value.trim();
          if (name && groupPrompt) {
            if (groupPrompt.mode === 'create') {
              actions.createGroup.mutate(name);
            } else {
              actions.renameGroup.mutate({ groupId: groupPrompt.groupId, name });
            }
          }
          setGroupPrompt(null);
        }}
      />
      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        description={confirm?.description}
        danger={confirm?.danger}
        onClose={() => setConfirm(null)}
        onConfirm={() => {
          confirm?.run();
          setConfirm(null);
        }}
      />
    </div>
  );
}

function AddFriendDialog({ open, onClose }: { open: boolean; onClose: () => void }): React.JSX.Element {
  const [keyword, setKeyword] = useState('');
  const debounced = useDebouncedValue(keyword, 300);
  const search = useUserSearch(debounced);
  const friends = useFriendsQuery();
  const actions = useContactActions();
  const friendIds = new Set((friends.data ?? []).map((friend) => friend.userId));

  return (
    <Dialog open={open} onClose={onClose} title="添加好友" maxWidth={400}>
      <div className="grid gap-3">
        <div className="flex h-10 items-center gap-2 rounded-lg bg-black/[0.05] px-3 transition focus-within:bg-white focus-within:ring-[3px] focus-within:ring-[#0071e3]/15">
          <SearchOutlined aria-hidden className="text-[#86868b]" />
          <input
            type="search"
            name="add-friend-search"
            autoComplete="off"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索用户名 / 手机号 / 邮箱"
            aria-label="搜索用户"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-[#a1a1a6]"
          />
          {search.isFetching ? <Spinner size={14} /> : null}
        </div>
        <div className="max-h-64 overflow-y-auto rounded-lg border border-black/[0.06]">
          {(search.data ?? []).length === 0 ? (
            <p className="p-5 text-center text-xs text-[#86868b]">{debounced.trim() ? '未找到相关用户' : '输入关键词搜索用户'}</p>
          ) : (
            <ul className="m-0 list-none p-1">
              {(search.data ?? []).map((user) => {
                const already = friendIds.has(user.id);
                return (
                  <li key={user.id} className="flex items-center gap-2.5 rounded-lg px-2.5 py-2">
                    <Avatar name={user.username} src={user.avatar || undefined} colorKey={user.id} size="sm" />
                    <span className="min-w-0 flex-1 truncate text-sm text-[#1d1d1f]">{user.username}</span>
                    <button
                      type="button"
                      disabled={already || actions.sendRequest.isPending}
                      onClick={() => actions.sendRequest.mutate({ toUserId: user.id, message: '你好，我想加你为好友' })}
                      className="shrink-0 rounded-lg bg-[#0071e3]/10 px-2.5 py-1 text-xs font-medium text-[#0071e3] transition hover:bg-[#0071e3]/15 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {already ? '已是好友' : '发送申请'}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// 新的朋友
// ---------------------------------------------------------------------------
function RequestsTab(): React.JSX.Element {
  const pending = usePendingRequestsQuery();
  const sent = useSentRequestsQuery();
  const actions = useContactActions();
  const STATUS_LABEL: Record<number, string> = { 1: '等待验证', 2: '已通过', 3: '已拒绝', 4: '已取消' };

  return (
    <div className="mx-auto grid max-w-2xl gap-6 p-5">
      <section>
        <h2 className="px-1 pb-2 text-xs font-semibold text-[#86868b]">收到的申请</h2>
        {(pending.data ?? []).length === 0 ? (
          <p className="rounded-xl bg-white p-5 text-center text-xs text-[#86868b]">暂无新的好友申请</p>
        ) : (
          <ul className="m-0 grid list-none gap-1 p-0">
            {(pending.data ?? []).map((request) => (
              <li key={request.requestId} className="flex items-center gap-3 rounded-xl bg-white px-3 py-3 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                <Avatar name={request.username} src={request.avatar || undefined} colorKey={request.userId} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-[#1d1d1f]">{request.username}</span>
                  <span className="block truncate text-xs text-[#86868b]">{request.message || '请求添加你为好友'}</span>
                </span>
                <button
                  type="button"
                  onClick={() => actions.reject.mutate(request.requestId)}
                  className="shrink-0 rounded-lg border border-black/[0.12] px-3 py-1.5 text-xs font-medium text-[#424245] transition hover:border-black/[0.25]"
                >
                  拒绝
                </button>
                <button
                  type="button"
                  onClick={() => actions.accept.mutate(request.requestId)}
                  className="shrink-0 rounded-lg bg-[#0071e3] px-3 py-1.5 text-xs font-medium text-white transition hover:bg-[#0077ed]"
                >
                  接受
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="px-1 pb-2 text-xs font-semibold text-[#86868b]">我发出的申请</h2>
        {(sent.data ?? []).length === 0 ? (
          <p className="rounded-xl bg-white p-5 text-center text-xs text-[#86868b]">暂无发出的申请</p>
        ) : (
          <ul className="m-0 grid list-none gap-1 p-0">
            {(sent.data ?? []).map((request) => (
              <li key={request.requestId} className="flex items-center gap-3 rounded-xl bg-white px-3 py-3 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
                <Avatar name={request.username} src={request.avatar || undefined} colorKey={request.userId} size="sm" />
                <span className="min-w-0 flex-1 truncate text-sm text-[#1d1d1f]">{request.username}</span>
                <span className="shrink-0 text-xs text-[#86868b]">{STATUS_LABEL[request.status]}</span>
                {request.status === 1 ? (
                  <button
                    type="button"
                    onClick={() => actions.cancel.mutate(request.requestId)}
                    className="shrink-0 rounded-lg px-2 py-1 text-xs text-[#86868b] transition hover:bg-black/[0.05]"
                  >
                    撤回
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 黑名单
// ---------------------------------------------------------------------------
function BlacklistTab(): React.JSX.Element {
  const blacklist = useBlacklistQuery();
  const actions = useContactActions();

  return (
    <div className="mx-auto max-w-2xl p-5">
      {(blacklist.data ?? []).length === 0 ? (
        <EmptyState title="黑名单为空" description="被拉黑的用户无法向你发送好友申请" />
      ) : (
        <ul className="m-0 grid list-none gap-1 p-0">
          {(blacklist.data ?? []).map((entry) => (
            <li key={entry.userId} className="flex items-center gap-3 rounded-xl bg-white px-3 py-2.5 shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
              <Avatar name={entry.username} src={entry.avatar || undefined} colorKey={entry.userId} size="sm" />
              <span className="min-w-0 flex-1 truncate text-sm text-[#1d1d1f]">{entry.username}</span>
              <button
                type="button"
                onClick={() => actions.unblock.mutate(entry.userId)}
                className="shrink-0 rounded-lg border border-black/[0.12] px-3 py-1.5 text-xs font-medium text-[#424245] transition hover:border-black/[0.25]"
              >
                移出黑名单
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
