import { EditOutlined, UserAddOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { PromptDialog } from '@/components/ui/PromptDialog';
import { Switch } from '@/components/ui/Switch';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useAdminActions, useMembersQuery, useUpdateSettings } from '../hooks';
import type { UiConversation } from '../model';
import { InviteMembersDialog } from './InviteMembersDialog';
import { MemberList } from './MemberList';

/**
 * 会话详情（右栏）：概要 / 公告 / 我的设置（置顶·免打扰·群昵称）/ 成员管理。
 * 「退出群聊」当前后端未提供接口（api-v1.md §5.5 未实现），置灰并注明。
 */
export function ConversationDetailPanel({ conversation }: { conversation: UiConversation | null }): React.JSX.Element {
  const currentUser = useCurrentUser();
  const members = useMembersQuery(conversation?.id ?? null);
  const settings = useUpdateSettings(conversation?.id ?? '');
  const admin = useAdminActions(conversation?.id ?? '');
  const [editingAnnouncement, setEditingAnnouncement] = useState(false);
  const [editingNickname, setEditingNickname] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{ title: string; description: string; run: () => void } | null>(null);

  if (!conversation || !currentUser) {
    return <aside aria-label="会话详情" className="h-full bg-[#fbfbfd]" />;
  }

  const isGroup = conversation.type === 'group';
  const myRole = (members.data ?? []).find((member) => member.userId === currentUser.id)?.role ?? 0;
  const isManager = myRole === 1 || myRole === 2;

  return (
    <aside aria-label="会话详情" className="flex h-full min-h-0 flex-col overflow-y-auto bg-[#fbfbfd] px-5 py-6">
      {/* 概要 */}
      <div className="grid justify-items-center gap-2 border-b border-black/[0.06] pb-5 text-center">
        <Avatar name={conversation.name} src={conversation.avatar || undefined} colorKey={conversation.id} size="xl" shape="rounded" />
        <h2 className="max-w-full truncate text-base font-semibold text-[#1d1d1f]">{conversation.name}</h2>
        <p className="text-xs text-[#86868b]">
          {isGroup ? `群聊 · ${conversation.memberCount} 位成员` : '单聊'}
          {conversation.isMutedAll ? ' · 全员禁言中' : ''}
        </p>
      </div>

      {/* 公告 */}
      {isGroup ? (
        <section className="border-b border-black/[0.06] py-4">
          <header className="mb-2 flex items-center justify-between">
            <h3 className="text-xs font-semibold text-[#86868b]">群公告</h3>
            {isManager ? (
              <button
                type="button"
                aria-label="编辑公告"
                onClick={() => setEditingAnnouncement(true)}
                className="grid size-6 place-items-center rounded-md text-[#86868b] transition hover:bg-black/[0.06] hover:text-[#1d1d1f]"
              >
                <EditOutlined aria-hidden />
              </button>
            ) : null}
          </header>
          <p className="min-w-0 text-[13px] leading-6 break-words whitespace-pre-wrap text-[#424245]">
            {conversation.announcement || '暂无公告'}
          </p>
        </section>
      ) : null}

      {/* 我的设置 */}
      <section className="grid gap-3 border-b border-black/[0.06] py-4">
        <h3 className="text-xs font-semibold text-[#86868b]">会话设置</h3>
        <div className="flex items-center justify-between">
          <span className="text-[13px] text-[#1d1d1f]">置顶会话</span>
          <Switch checked={conversation.isPinned} label="置顶会话" onChange={(checked) => settings.mutate({ isPinned: checked })} />
        </div>
        <div className="flex items-center justify-between">
          <span className="text-[13px] text-[#1d1d1f]">消息免打扰</span>
          <Switch checked={conversation.isDnd} label="消息免打扰" onChange={(checked) => settings.mutate({ isMuted: checked })} />
        </div>
        {isGroup ? (
          <button
            type="button"
            onClick={() => setEditingNickname(true)}
            className="flex items-center justify-between rounded-lg py-1 text-left transition hover:opacity-70"
          >
            <span className="text-[13px] text-[#1d1d1f]">我在本群的昵称</span>
            <span className="max-w-28 truncate text-[13px] text-[#86868b]">{conversation.nickname || '未设置'}</span>
          </button>
        ) : null}
      </section>

      {/* 成员 */}
      {isGroup ? (
        <section className="py-4">
          <header className="mb-2 flex items-center justify-between">
            <h3 className="text-xs font-semibold text-[#86868b]">成员（{conversation.memberCount}）</h3>
            {isManager ? (
              <button
                type="button"
                aria-label="邀请成员"
                onClick={() => setInviteOpen(true)}
                className="grid size-6 place-items-center rounded-md text-[#86868b] transition hover:bg-black/[0.06] hover:text-[#1d1d1f]"
              >
                <UserAddOutlined aria-hidden />
              </button>
            ) : null}
          </header>
          {isManager ? (
            <MemberList
              members={members.data ?? []}
              currentUserId={currentUser.id}
              myRole={myRole}
              onMute={(userId, durationSeconds) => admin.mute.mutate({ userId, durationSeconds })}
              onUnmute={(userId) => admin.unmute.mutate(userId)}
              onKick={(userId) => {
                const name = (members.data ?? []).find((member) => member.userId === userId)?.displayName ?? '该成员';
                setConfirmAction({
                  title: '移出群聊',
                  description: `确定将 ${name} 移出群聊吗？`,
                  run: () => admin.kick.mutate([userId]),
                });
              }}
              onTransfer={(userId) => {
                const name = (members.data ?? []).find((member) => member.userId === userId)?.displayName ?? '该成员';
                setConfirmAction({
                  title: '转让群主',
                  description: `确定将群主转让给 ${name} 吗？转让后你将成为普通成员。`,
                  run: () => admin.transfer.mutate(userId),
                });
              }}
            />
          ) : (
            <MemberList
              members={members.data ?? []}
              currentUserId={currentUser.id}
              myRole={0}
              onMute={() => undefined}
              onUnmute={() => undefined}
              onKick={() => undefined}
              onTransfer={() => undefined}
            />
          )}
        </section>
      ) : null}

      {/* 危险区 */}
      {isGroup ? (
        <div className="mt-auto pt-4">
          <button
            type="button"
            disabled
            title="后端暂未提供退出群聊接口（api-v1.md §5.5 未实现）"
            className="w-full cursor-not-allowed rounded-lg border border-[#e5484d]/30 py-2 text-sm font-medium text-[#e5484d]/50"
          >
            退出群聊
          </button>
          <p className="mt-1.5 text-center text-[10px] text-[#c7c7cc]">后端暂未提供退群接口</p>
        </div>
      ) : null}

      {/* 对话框 */}
      <PromptDialog
        open={editingAnnouncement}
        title="编辑群公告"
        initialValue={conversation.announcement}
        placeholder="输入公告内容（留空即清除公告）"
        maxLength={500}
        multiline
        pending={admin.saveAnnouncement.isPending}
        onClose={() => setEditingAnnouncement(false)}
        onSubmit={(value) => {
          admin.saveAnnouncement.mutate(value, { onSuccess: () => setEditingAnnouncement(false) });
        }}
      />
      <PromptDialog
        open={editingNickname}
        title="我在本群的昵称"
        initialValue={conversation.nickname}
        placeholder="输入群昵称"
        maxLength={20}
        pending={settings.isPending}
        onClose={() => setEditingNickname(false)}
        onSubmit={(value) => {
          settings.mutate({ nickname: value.trim() });
          setEditingNickname(false);
        }}
      />
      <InviteMembersDialog
        open={inviteOpen}
        conversationId={conversation.id}
        existingMemberIds={(members.data ?? []).map((member) => member.userId)}
        onClose={() => setInviteOpen(false)}
      />
      <ConfirmDialog
        open={confirmAction !== null}
        title={confirmAction?.title ?? ''}
        description={confirmAction?.description}
        danger
        onClose={() => setConfirmAction(null)}
        onConfirm={() => {
          confirmAction?.run();
          setConfirmAction(null);
        }}
      />
    </aside>
  );
}
