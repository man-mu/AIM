import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { friendApi } from '@/apis/friend';
import { queryKeys } from '@/apis/queryKeys';
import { toast } from '@/components/ui/toast/toastBus';
import { toInt64String } from '@/lib/ids';
import type { BlacklistEntryDTO, FriendDTO, FriendGroupDTO, FriendRequestDTO } from '@/types/Friend/Friend';

/** 联系人域 hooks（friend-service 规划态接口，mock 实现）。 */

export interface UiFriend {
  userId: string;
  username: string;
  avatar: string;
  remark: string;
  displayName: string;
  groupId: string;
  groupName: string;
  online: boolean;
  createdAt: number;
}

export interface UiFriendRequest {
  requestId: string;
  userId: string;
  username: string;
  avatar: string;
  message: string;
  status: 1 | 2 | 3 | 4;
  createdAt: number;
}

export interface UiFriendGroup {
  groupId: string;
  name: string;
  friendCount: number;
}

function mapFriend(dto: FriendDTO): UiFriend {
  return {
    userId: toInt64String(dto.userId),
    username: dto.username,
    avatar: dto.avatar,
    remark: dto.remark,
    displayName: dto.remark || dto.username,
    groupId: toInt64String(dto.groupId),
    groupName: dto.groupName,
    online: dto.status === 'online',
    createdAt: dto.createdAt,
  };
}

function mapIncomingRequest(dto: FriendRequestDTO): UiFriendRequest {
  return {
    requestId: toInt64String(dto.requestId),
    userId: toInt64String(dto.fromUserId),
    username: dto.fromUsername,
    avatar: dto.fromAvatar,
    message: dto.message,
    status: dto.status,
    createdAt: dto.createdAt,
  };
}

function mapOutgoingRequest(dto: FriendRequestDTO): UiFriendRequest {
  return {
    requestId: toInt64String(dto.requestId),
    userId: toInt64String(dto.toUserId),
    username: dto.toUsername ?? '未知用户',
    avatar: dto.toAvatar ?? '',
    message: dto.message,
    status: dto.status,
    createdAt: dto.createdAt,
  };
}

export function useFriendsQuery() {
  return useQuery<UiFriend[]>({
    queryKey: queryKeys.friends.list,
    queryFn: async ({ signal }) => {
      const data = await friendApi.list({ pageNum: 1, pageSize: 200 }, { signal });
      return data.list.map(mapFriend);
    },
    staleTime: 60_000,
  });
}

export function useFriendGroupsQuery() {
  return useQuery<UiFriendGroup[]>({
    queryKey: queryKeys.friends.groups,
    queryFn: async ({ signal }) => {
      const data = await friendApi.listGroups({ signal });
      return data.list.map((group: FriendGroupDTO) => ({
        groupId: toInt64String(group.groupId),
        name: group.name,
        friendCount: group.friendCount,
      }));
    },
    staleTime: 60_000,
  });
}

export function usePendingRequestsQuery() {
  return useQuery<UiFriendRequest[]>({
    queryKey: queryKeys.friends.incomingRequests,
    queryFn: async ({ signal }) => {
      const data = await friendApi.pendingRequests({ signal });
      return data.list.map(mapIncomingRequest);
    },
    staleTime: 30_000,
  });
}

export function useSentRequestsQuery() {
  return useQuery<UiFriendRequest[]>({
    queryKey: queryKeys.friends.outgoingRequests,
    queryFn: async ({ signal }) => {
      const data = await friendApi.sentRequests({ signal });
      return data.list.map(mapOutgoingRequest);
    },
    staleTime: 30_000,
  });
}

export function useBlacklistQuery() {
  return useQuery({
    queryKey: queryKeys.friends.blacklist,
    queryFn: async ({ signal }) => {
      const data = await friendApi.blacklist({ signal });
      return data.list.map((entry: BlacklistEntryDTO) => ({
        userId: toInt64String(entry.userId),
        username: entry.username,
        avatar: entry.avatar,
        createdAt: entry.createdAt,
      }));
    },
    staleTime: 60_000,
  });
}

/** 联系人域 mutations：统一失效相关缓存 + Toast 反馈。 */
export function useContactActions() {
  const queryClient = useQueryClient();
  const invalidateAll = (): void => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.friends.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
  };
  const onError = (error: unknown): void => {
    toast.error(error instanceof Error ? error.message : '操作失败');
  };

  const sendRequest = useMutation({
    mutationFn: ({ toUserId, message }: { toUserId: string; message: string }) =>
      friendApi.sendRequest(toUserId, message),
    onSuccess: () => {
      toast.success('好友申请已发送');
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.outgoingRequests });
    },
    onError,
  });

  const accept = useMutation({
    mutationFn: (requestId: string) => friendApi.acceptRequest(requestId),
    onSuccess: () => {
      toast.success('已添加为好友');
      invalidateAll();
    },
    onError,
  });

  const reject = useMutation({
    mutationFn: (requestId: string) => friendApi.rejectRequest(requestId),
    onSuccess: invalidateAll,
    onError,
  });

  const cancel = useMutation({
    mutationFn: (requestId: string) => friendApi.cancelRequest(requestId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.friends.outgoingRequests }),
    onError,
  });

  const removeFriend = useMutation({
    mutationFn: (friendId: string) => friendApi.remove(friendId),
    onSuccess: () => {
      toast.success('已删除好友');
      invalidateAll();
    },
    onError,
  });

  const setRemark = useMutation({
    mutationFn: ({ friendId, remark }: { friendId: string; remark: string }) => friendApi.setRemark(friendId, remark),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.friends.list }),
    onError,
  });

  const moveToGroup = useMutation({
    mutationFn: ({ friendId, groupId }: { friendId: string; groupId: string }) =>
      friendApi.moveToGroup(friendId, groupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.list });
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.groups });
    },
    onError,
  });

  const createGroup = useMutation({
    mutationFn: (name: string) => friendApi.createGroup(name),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.friends.groups }),
    onError,
  });

  const renameGroup = useMutation({
    mutationFn: ({ groupId, name }: { groupId: string; name: string }) => friendApi.renameGroup(groupId, name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.groups });
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.list });
    },
    onError,
  });

  const deleteGroup = useMutation({
    mutationFn: (groupId: string) => friendApi.deleteGroup(groupId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.groups });
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.list });
    },
    onError,
  });

  const block = useMutation({
    mutationFn: (userId: string) => friendApi.block(userId),
    onSuccess: () => {
      toast.success('已拉黑');
      invalidateAll();
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.blacklist });
    },
    onError,
  });

  const unblock = useMutation({
    mutationFn: (userId: string) => friendApi.unblock(userId),
    onSuccess: () => {
      toast.success('已移出黑名单');
      void queryClient.invalidateQueries({ queryKey: queryKeys.friends.blacklist });
    },
    onError,
  });

  return { sendRequest, accept, reject, cancel, removeFriend, setRemark, moveToGroup, createGroup, renameGroup, deleteGroup, block, unblock };
}
