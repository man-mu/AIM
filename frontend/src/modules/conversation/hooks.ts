import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import { convApi } from '@/apis/conv';
import { queryKeys } from '@/apis/queryKeys';
import { toast } from '@/components/ui/toast/toastBus';
import { mapWithConcurrencySettled } from '@/lib/async';
import type { CreateConversationParams, UpdateSettingsParams } from '@/types/Conversation/Conversation';
import { clearUnread, patchConversation, upsertConversation } from './cache';
import { mapConversation, mapMember, sortConversations, type UiConversation, type UiMember } from './model';

/**
 * 会话域 hooks：Query 缓存里存的是 UiConversation[]（已合并 settings、已排序）。
 *
 * 关于 settings 的 N+1：实现态接口的列表不返回 isPinned/isMuted，
 * 前端以受控并发（≤6）批量拉取首屏会话的 settings 合并进列表
 * —— 该接口设计问题已记录在 docs/api-feedback.md，等待后端在列表响应中回填。
 */
export function useConversationsQuery() {
  return useQuery<UiConversation[]>({
    queryKey: queryKeys.conversations.list,
    queryFn: async ({ signal }) => {
      const data = await convApi.list({ pageNum: 1, pageSize: 100 }, { signal });
      const settingsList = await mapWithConcurrencySettled(
        data.conversations,
        6,
        (dto) => convApi.getSettings(dto.id, { signal }),
        () => null,
      );
      return sortConversations(data.conversations.map((dto, index) => mapConversation(dto, settingsList[index])));
    },
    staleTime: 30_000,
  });
}

/** 从列表缓存派生单个会话（选中态、头部、详情面板共用）。 */
export function useConversation(conversationId: string | null): UiConversation | null {
  const { data } = useConversationsQuery();
  return useMemo(() => {
    if (!conversationId || !data) {
      return null;
    }
    return data.find((conversation) => conversation.id === conversationId) ?? null;
  }, [conversationId, data]);
}

export function useMembersQuery(conversationId: string | null) {
  return useQuery<UiMember[]>({
    queryKey: queryKeys.conversations.members(conversationId ?? 'none'),
    queryFn: async ({ signal }) => {
      const data = await convApi.getMembers(conversationId as string, { pageNum: 1, pageSize: 200 }, { signal });
      return data.members.map(mapMember);
    },
    enabled: conversationId !== null,
    staleTime: 60_000,
  });
}

export function useCreateConversation() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: async (params: CreateConversationParams) => {
      const data = await convApi.create(params);
      const settings = await convApi.getSettings(data.conversationId).catch(() => null);
      return mapConversation(data.conversation, settings);
    },
    onSuccess: (conversation) => {
      queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
        upsertConversation(old, conversation),
      );
      void navigate(`/home/${conversation.id}`);
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : '创建会话失败');
    },
  });
}

/** 设置更新：乐观写缓存，失败回滚（invalidate 兜底）。 */
export function useUpdateSettings(conversationId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (patch: UpdateSettingsParams) => convApi.updateSettings(conversationId, patch),
    onMutate: (patch) => {
      queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
        patchConversation(old, conversationId, {
          ...(patch.isPinned !== undefined ? { isPinned: patch.isPinned } : {}),
          ...(patch.isMuted !== undefined ? { isDnd: patch.isMuted } : {}),
          ...(patch.nickname !== undefined ? { nickname: patch.nickname } : {}),
        }),
      );
      return undefined;
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : '设置更新失败');
      void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.list });
    },
  });
}

/** 标记已读：本地未读立即归零 + 服务端落位。 */
export function useMarkRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ conversationId, seq }: { conversationId: string; seq: number }) =>
      convApi.markRead(conversationId, seq),
    onMutate: ({ conversationId }) => {
      queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
        clearUnread(old, conversationId),
      );
      return undefined;
    },
  });
}

/** 群管理动作：成功后失效成员/列表（系统消息与事件由 mock/信令自然推进）。 */
export function useAdminActions(conversationId: string) {
  const queryClient = useQueryClient();

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.members(conversationId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.conversations.list });
  };
  const onError = (error: unknown): void => {
    toast.error(error instanceof Error ? error.message : '操作失败');
  };

  const invite = useMutation({
    mutationFn: (userIds: string[]) => convApi.invite(conversationId, userIds),
    onSuccess: (data) => {
      if (data.alreadyMemberIds.length > 0) {
        toast.info(`${data.alreadyMemberIds.length} 人已在群内`);
      }
      invalidate();
    },
    onError,
  });
  const kick = useMutation({
    mutationFn: (userIds: string[]) => convApi.kick(conversationId, userIds),
    onSuccess: invalidate,
    onError,
  });
  const mute = useMutation({
    mutationFn: ({ userId, durationSeconds }: { userId: string; durationSeconds: number }) =>
      convApi.muteMember(conversationId, userId, durationSeconds),
    onSuccess: () => {
      toast.success('已禁言');
      invalidate();
    },
    onError,
  });
  const unmute = useMutation({
    mutationFn: (userId: string) => convApi.unmuteMember(conversationId, userId),
    onSuccess: () => {
      toast.success('已解除禁言');
      invalidate();
    },
    onError,
  });
  const transfer = useMutation({
    mutationFn: (newOwnerId: string) => convApi.transferOwner(conversationId, newOwnerId),
    onSuccess: () => {
      toast.success('已转让群主');
      invalidate();
    },
    onError,
  });
  const saveAnnouncement = useMutation({
    mutationFn: (content: string) =>
      content.trim() === ''
        ? convApi.deleteAnnouncement(conversationId)
        : convApi.setAnnouncement(conversationId, content),
    onSuccess: () => {
      toast.success('公告已更新');
      invalidate();
    },
    onError,
  });

  return { invite, kick, mute, unmute, transfer, saveAnnouncement };
}
