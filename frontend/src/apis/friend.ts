import { request } from './request.ts';
import type {
  BlacklistEntryDTO,
  FriendDTO,
  FriendGroupDTO,
  FriendRequestDTO,
  PagedList,
} from '@/types/Friend/Friend';
import type { Int64 } from '@/types/User/User';

/** Friend API（api-v1.md §4 规划态接口，当前由 Mock 平台实现）。 */
export const friendApi = {
  sendRequest: (toUserId: Int64, message: string) => {
    return request<{ requestId: Int64 }>('/friends/requests', 'POST', { toUserId, message });
  },
  acceptRequest: (requestId: Int64) => {
    return request<FriendRequestDTO>(`/friends/requests/${requestId}/accept`, 'POST', {});
  },
  rejectRequest: (requestId: Int64) => {
    return request<FriendRequestDTO>(`/friends/requests/${requestId}/reject`, 'POST', {});
  },
  cancelRequest: (requestId: Int64) => {
    return request(`/friends/requests/${requestId}`, 'DELETE');
  },
  pendingRequests: (options?: { signal?: AbortSignal }) => {
    return request<PagedList<FriendRequestDTO>>('/friends/requests/pending', 'GET', { pageNum: 1, pageSize: 50 }, options);
  },
  sentRequests: (options?: { signal?: AbortSignal }) => {
    return request<PagedList<FriendRequestDTO>>('/friends/requests/sent', 'GET', { pageNum: 1, pageSize: 50 }, options);
  },
  list: (params?: { groupId?: Int64; pageNum?: number; pageSize?: number }, options?: { signal?: AbortSignal }) => {
    return request<PagedList<FriendDTO>>('/friends', 'GET', { pageNum: 1, pageSize: 100, ...params }, options);
  },
  remove: (friendId: Int64) => {
    return request(`/friends/${friendId}`, 'DELETE');
  },
  setRemark: (friendId: Int64, remark: string) => {
    return request(`/friends/${friendId}/remark`, 'PUT', { remark });
  },
  moveToGroup: (friendId: Int64, groupId: Int64) => {
    return request(`/friends/${friendId}/group`, 'PUT', { groupId });
  },
  listGroups: (options?: { signal?: AbortSignal }) => {
    return request<{ list: FriendGroupDTO[]; total: number }>('/friends/groups', 'GET', undefined, options);
  },
  createGroup: (name: string) => {
    return request<{ groupId: Int64; name: string }>('/friends/groups', 'POST', { name });
  },
  renameGroup: (groupId: Int64, name: string) => {
    return request<{ groupId: Int64; name: string }>(`/friends/groups/${groupId}`, 'PUT', { name });
  },
  deleteGroup: (groupId: Int64) => {
    return request(`/friends/groups/${groupId}`, 'DELETE');
  },
  blacklist: (options?: { signal?: AbortSignal }) => {
    return request<PagedList<BlacklistEntryDTO>>('/friends/blacklist', 'GET', { pageNum: 1, pageSize: 100 }, options);
  },
  block: (userId: Int64) => {
    return request(`/friends/blacklist/${userId}`, 'POST', {});
  },
  unblock: (userId: Int64) => {
    return request(`/friends/blacklist/${userId}`, 'DELETE');
  },
};
