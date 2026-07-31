import { SearchOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { Dialog } from '@/components/ui/Dialog';
import { Spinner } from '@/components/ui/Spinner';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { useUserSearch, type UiUser } from '@/modules/user/hooks';
import { useAdminActions } from '../hooks';

/** 邀请成员：搜索用户 → 多选 → 邀请（已在群成员置灰）。 */
export function InviteMembersDialog({
  open,
  conversationId,
  existingMemberIds,
  onClose,
}: {
  open: boolean;
  conversationId: string;
  existingMemberIds: string[];
  onClose: () => void;
}): React.JSX.Element {
  const [keyword, setKeyword] = useState('');
  const debounced = useDebouncedValue(keyword, 300);
  const search = useUserSearch(debounced);
  const [selected, setSelected] = useState<Map<string, UiUser>>(new Map());
  const admin = useAdminActions(conversationId);
  const existing = new Set(existingMemberIds);

  useEffect(() => {
    if (open) {
      setKeyword('');
      setSelected(new Map());
    }
  }, [open]);

  const toggle = (user: UiUser): void => {
    if (existing.has(user.id)) {
      return;
    }
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

  const submit = (): void => {
    const ids = [...selected.keys()];
    if (ids.length === 0) {
      return;
    }
    admin.invite.mutate(ids, { onSettled: onClose });
  };

  return (
    <Dialog open={open} onClose={onClose} title="邀请成员" maxWidth={400}>
      <div className="grid gap-3">
        <div className="flex h-10 items-center gap-2 rounded-lg bg-black/[0.05] px-3 transition focus-within:bg-white focus-within:ring-[3px] focus-within:ring-[#0071e3]/15">
          <SearchOutlined aria-hidden className="text-[#86868b]" />
          <input
            type="search"
            name="invite-user-search"
            autoComplete="off"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索用户"
            aria-label="搜索用户"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-[#a1a1a6]"
          />
          {search.isFetching ? <Spinner size={14} /> : null}
        </div>

        <div className="max-h-60 overflow-y-auto rounded-lg border border-black/[0.06]">
          {(search.data ?? []).length === 0 ? (
            <p className="p-5 text-center text-xs text-[#86868b]">
              {debounced.trim() ? '未找到相关用户' : '输入关键词搜索用户'}
            </p>
          ) : (
            <ul className="m-0 list-none p-1">
              {(search.data ?? []).map((user) => {
                const isMember = existing.has(user.id);
                const checked = selected.has(user.id);
                return (
                  <li key={user.id}>
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      disabled={isMember}
                      aria-label={`选择 ${user.username}`}
                      onClick={() => toggle(user)}
                      className={`flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left transition disabled:opacity-45 ${
                        checked ? 'bg-[#0071e3]/8' : 'hover:bg-black/[0.04]'
                      }`}
                    >
                      <Avatar name={user.username} src={user.avatar || undefined} colorKey={user.id} size="sm" />
                      <span className="min-w-0 flex-1 truncate text-sm text-[#1d1d1f]">{user.username}</span>
                      <span className="shrink-0 text-[11px] text-[#86868b]">{isMember ? '已在群内' : checked ? '✓' : ''}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <button
          type="button"
          disabled={selected.size === 0 || admin.invite.isPending}
          onClick={submit}
          className="h-10 rounded-lg bg-[#0071e3] text-sm font-medium text-white transition hover:bg-[#0077ed] disabled:cursor-not-allowed disabled:bg-[#d2d2d7]"
        >
          {admin.invite.isPending ? '邀请中…' : `邀请（${selected.size}）`}
        </button>
      </div>
    </Dialog>
  );
}
