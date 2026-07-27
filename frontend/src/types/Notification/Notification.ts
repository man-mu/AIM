import type { Int64 } from '../User/User';

/** 通知类型：1=系统 2=审核 3=Bot（Phase 2）。 */
export type NotificationType = 1 | 2 | 3;

export interface NotificationDTO {
  id: Int64;
  userId: Int64;
  type: NotificationType;
  title: string;
  content: string;
  isRead: boolean;
  referenceId: string;
  createdAt: number;
}

export interface UnreadCountData {
  count: number;
}
