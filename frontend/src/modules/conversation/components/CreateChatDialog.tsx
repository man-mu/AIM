import { SearchOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Dialog } from '@/components/ui/Dialog';
import { Spinner } from '@/components/ui/Spinner';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useUserSearch, type UiUser } from '@/modules/user/hooks';
import { useCreateConversation } from '../hooks';

/**
 * 发起会话：搜索用户 → 选 1 人直达单聊；选多人填群名建群。
 * 单聊创建具备幂等性（重复创建返回已有会话，直接跳转）。
 */
export function CreateChatDialog({ open, onClose }: { open: boolean; onClose: () => void }): React.JSX.Element {
  const currentUser = useCurrentUser();
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const search = useUserSearch(debouncedKeyword);
  const [selected, setSelected] = useState<Map<string, UiUser>>(new Map());
  const [groupName, setGroupName] = useState('');
  const create = useCreateConversation();

  useEffect(() => {
    if (open) {
      setKeyword('');
      setSelected(new Map());
      setGroupName('');
    }
  }, [open]);

  const toggle = (user: UiUser): void => {
    setSelected((current) => {
      const next = new Map(current);
      if (next.has(user.id)) {
        next.delete(user.id);
      } else {
        next.set(user.id, user);
      }
      return next;
    });
  };

  const selectedUsers = [...selected.values()];
  const isGroup = selectedUsers.length > 1;
  const canSubmit = selectedUsers.length > 0 && !create.isPending && (!isGroup || groupName.trim().length > 0);

  const submit = (): void => {
    if (!canSubmit) {
      return;
    }
    if (isGroup) {
      create.mutate(
        { type: 2, name: groupName.trim(), memberIds: selectedUsers.map((user) => user.id) },
        { onSettled: onClose },
      );
    } else {
      create.mutate({ type: 1, peerUserId: (selectedUsers[0] as UiUser).id }, { onSettled: onClose });
    }
  };

  const results = (search.data ?? []).filter((user) => user.id !== currentUser?.id);

  return (
    <Dialog open={open} onClose={onClose} title="发起会话" maxWidth={420}>
      <div className="grid gap-3">
        <div className="flex h-10 items-center gap-2 rounded-lg bg-black/[0.05] px-3 transition focus-within:bg-white focus-within:ring-[3px] focus-within:ring-[#0071e3]/15">
          <SearchOutlined aria-hidden className="text-[#86868b]" />
          <input
            type="search"
            name="user-search"
            autoComplete="off"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索用户名 / 手机号 / 邮箱"
            aria-label="搜索用户"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-[#a1a1a6]"
          />
          {search.isFetching ? <Spinner size={14} /> : null}
        </div>

        {selectedUsers.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {selectedUsers.map((user) => (
              <button
                key={user.id}
                type="button"
                onClick={() => toggle(user)}
                aria-label={`移除 ${user.username}`}
                className="flex items-center gap-1.5 rounded-full bg-[#0071e3]/10 py-1 pr-2.5 pl-1 text-xs font-medium text-[#0071e3] transition hover:bg-[#0071e3]/15"
              >
                <Avatar name={user.username} src={user.avatar || undefined} colorKey={user.id} size="sm" />
                {user.username} ×
              </button>
            ))}
          </div>
        ) : null}

        <div className="max-h-64 overflow-y-auto rounded-lg border border-black/[0.06]">
          {debouncedKeyword.trim() === '' ? (
            <p className="p-6 text-center text-xs text-[#86868b]">输入关键词搜索用户</p>
          ) : results.length === 0 && !search.isFetching ? (
            <p className="p-6 text-center text-xs text-[#86868b]">未找到相关用户</p>
          ) : (
            <ul className="m-0 list-none p-1">
              {results.map((user) => {
                const checked = selected.has(user.id);
                return (
                  <li key={user.id}>
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      aria-label={`选择 ${user.username}`}
                      onClick={() => toggle(user)}
                      className={`flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition ${
                        checked ? 'bg-[#0071e3]/8' : 'hover:bg-black/[0.04]'
                      }`}
                    >
                      <Avatar name={user.username} src={user.avatar || undefined} colorKey={user.id} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-[#1d1d1f]">{user.username}</span>
                        <span className="block truncate text-[11px] text-[#86868b]">{user.bio || user.email || user.phone}</span>
                      </span>
                      <span
                        aria-hidden
                        className={`grid size-5 shrink-0 place-items-center rounded-full border text-[10px] text-white transition ${
                          checked ? 'border-[#0071e3] bg-[#0071e3]' : 'border-black/[0.2]'
                        }`}
                      >
                        {checked ? '✓' : ''}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {isGroup ? (
          <input
            name="group-name"
            autoComplete="off"
            value={groupName}
            onChange={(event) => setGroupName(event.target.value)}
            placeholder="群聊名称（必填）"
            aria-label="群聊名称"
            maxLength={32}
            className="w-full rounded-lg border border-black/[0.14] px-3 py-2 text-sm outline-none transition placeholder:text-[#a1a1a6] focus:border-[#0071e3] focus:ring-[3px] focus:ring-[#0071e3]/15"
          />
        ) : null}

        <button
          type="button"
          disabled={!canSubmit}
          onClick={submit}
          className="h-10 rounded-lg bg-[#0071e3] text-sm font-medium text-white transition hover:bg-[#0077ed] disabled:cursor-not-allowed disabled:bg-[#d2d2d7]"
        >
          {create.isPending ? '创建中…' : isGroup ? `创建群聊（${selectedUsers.length + 1} 人）` : '发起单聊'}
        </button>
      </div>
    </Dialog>
  );
}
