import type { Int64 } from '../User/User';

/** 好友申请状态：1=待处理 2=已接受 3=已拒绝 4=已取消。 */
export type FriendRequestStatus = 1 | 2 | 3 | 4;

export interface FriendDTO {
  userId: Int64;
  username: string;
  avatar: string;
  remark: string;
  groupId: Int64;
  groupName: string;
  status: 'online' | 'offline';
  createdAt: number;
}

export interface FriendRequestDTO {
  requestId: Int64;
  fromUserId: Int64;
  fromUsername: string;
  fromAvatar: string;
  toUserId: Int64;
  toUsername?: string;
  toAvatar?: string;
  message: string;
  status: FriendRequestStatus;
  createdAt: number;
  updatedAt: number;
}

export interface FriendGroupDTO {
  groupId: Int64;
  name: string;
  friendCount: number;
  createdAt: number;
}

export interface BlacklistEntryDTO {
  userId: Int64;
  username: string;
  avatar: string;
  createdAt: number;
}

export interface PagedList<T> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}
