import { request } from './request.ts';
import type { NotificationDTO, UnreadCountData } from '@/types/Notification/Notification';
import type { PagedList } from '@/types/Friend/Friend';
import type { Int64 } from '@/types/User/User';

/** Notification API（api-v1.md §8 规划态接口，当前由 Mock 平台实现）。 */
export const notificationApi = {
  list: (params?: { pageNum?: number; pageSize?: number; type?: number; isRead?: boolean }, options?: { signal?: AbortSignal }) => {
    return request<PagedList<NotificationDTO>>('/notifications', 'GET', { pageNum: 1, pageSize: 20, ...params }, options);
  },
  unreadCount: (options?: { signal?: AbortSignal }) => {
    return request<UnreadCountData>('/notifications/unread-count', 'GET', undefined, options);
  },
  markRead: (notificationId: Int64) => {
    return request(`/notifications/${notificationId}/read`, 'POST', {});
  },
  readAll: () => {
    return request('/notifications/read-all', 'POST', {});
  },
  delete: (notificationId: Int64) => {
    return request(`/notifications/${notificationId}`, 'DELETE');
  },
};
