import { nextId, pairKey, type DbState } from './state';
import { MockDbError, getUser, requireUser } from './users';
import type { BlacklistRow, FriendGroupRow, FriendRequestRow, FriendRow, NotificationRow } from './schema';

/** 好友 / 黑名单 / 分组 / 通知（friend-service + signaling 的 mock 侧实现）。 */

export function areFriends(state: DbState, a: string, b: string): boolean {
  return state.friends.has(pairKey(a, b));
}

export function isBlocked(state: DbState, ownerId: string, targetId: string): boolean {
  return state.blacklist.has(pairKey(ownerId, targetId));
}

export function addFriendPair(state: DbState, a: string, b: string, now: number): void {
  if (!state.friends.has(pairKey(a, b))) {
    state.friends.set(pairKey(a, b), { ownerId: a, friendId: b, remark: '', groupId: '0', createdAt: now });
  }
  if (!state.friends.has(pairKey(b, a))) {
    state.friends.set(pairKey(b, a), { ownerId: b, friendId: a, remark: '', groupId: '0', createdAt: now });
  }
}

export function removeFriendPair(state: DbState, a: string, b: string): void {
  if (!state.friends.has(pairKey(a, b))) {
    throw new MockDbError(20003, '不是好友关系');
  }
  state.friends.delete(pairKey(a, b));
  state.friends.delete(pairKey(b, a));
}

export function listFriends(state: DbState, ownerId: string, groupId?: string): FriendRow[] {
  const rows: FriendRow[] = [];
  for (const row of state.friends.values()) {
    if (row.ownerId !== ownerId) {
      continue;
    }
    if (groupId && groupId !== '0' && row.groupId !== groupId) {
      continue;
    }
    rows.push(row);
  }
  return rows.sort((a, b) => a.createdAt - b.createdAt);
}

export function setFriendRemark(state: DbState, ownerId: string, friendId: string, remark: string): void {
  const row = state.friends.get(pairKey(ownerId, friendId));
  if (!row) {
    throw new MockDbError(20003, '不是好友关系');
  }
  row.remark = remark;
}

export function moveFriendToGroup(state: DbState, ownerId: string, friendId: string, groupId: string): void {
  const row = state.friends.get(pairKey(ownerId, friendId));
  if (!row) {
    throw new MockDbError(20003, '不是好友关系');
  }
  if (groupId !== '0' && !state.friendGroups.get(groupId)) {
    throw new MockDbError(20005, '分组不存在');
  }
  row.groupId = groupId;
}

// ---------------------------------------------------------------------------
// 好友申请
// ---------------------------------------------------------------------------
export function createFriendRequest(state: DbState, fromUserId: string, toUserId: string, message: string, now: number): FriendRequestRow {
  requireUser(state, toUserId);
  if (fromUserId === toUserId) {
    throw new MockDbError(20004, '不能添加自己为好友');
  }
  if (areFriends(state, fromUserId, toUserId)) {
    throw new MockDbError(20001, '已是好友');
  }
  if (isBlocked(state, fromUserId, toUserId)) {
    throw new MockDbError(20006, '对方在你的黑名单中');
  }
  if (isBlocked(state, toUserId, fromUserId)) {
    throw new MockDbError(20007, '你已被对方拉黑');
  }
  for (const request of state.friendRequests.values()) {
    if (request.fromUserId === fromUserId && request.toUserId === toUserId && request.status === 1) {
      // 重复申请：刷新原申请即可，幂等返回。
      request.message = message;
      request.updatedAt = now;
      return request;
    }
  }

  const row: FriendRequestRow = {
    id: nextId(state, 'friendRequest'),
    fromUserId,
    toUserId,
    message,
    status: 1,
    createdAt: now,
    updatedAt: now,
  };
  state.friendRequests.set(row.id, row);
  return row;
}

export function requireFriendRequest(state: DbState, requestId: string): FriendRequestRow {
  const row = state.friendRequests.get(requestId);
  if (!row) {
    throw new MockDbError(20002, '好友申请不存在或已处理');
  }
  return row;
}

export function acceptFriendRequest(state: DbState, requestId: string, operatorId: string, now: number): FriendRequestRow {
  const row = requireFriendRequest(state, requestId);
  if (row.toUserId !== operatorId || row.status !== 1) {
    throw new MockDbError(20002, '好友申请不存在或已处理');
  }
  row.status = 2;
  row.updatedAt = now;
  addFriendPair(state, row.fromUserId, row.toUserId, now);
  return row;
}

export function rejectFriendRequest(state: DbState, requestId: string, operatorId: string, now: number): FriendRequestRow {
  const row = requireFriendRequest(state, requestId);
  if (row.toUserId !== operatorId || row.status !== 1) {
    throw new MockDbError(20002, '好友申请不存在或已处理');
  }
  row.status = 3;
  row.updatedAt = now;
  return row;
}

export function cancelFriendRequest(state: DbState, requestId: string, operatorId: string, now: number): FriendRequestRow {
  const row = requireFriendRequest(state, requestId);
  if (row.fromUserId !== operatorId || row.status !== 1) {
    throw new MockDbError(20002, '好友申请不存在或已处理');
  }
  row.status = 4;
  row.updatedAt = now;
  return row;
}

export function listFriendRequests(state: DbState, userId: string, direction: 'incoming' | 'outgoing'): FriendRequestRow[] {
  const rows: FriendRequestRow[] = [];
  for (const row of state.friendRequests.values()) {
    if (direction === 'incoming' && row.toUserId === userId) {
      rows.push(row);
    }
    if (direction === 'outgoing' && row.fromUserId === userId) {
      rows.push(row);
    }
  }
  return rows.sort((a, b) => b.createdAt - a.createdAt);
}

// ---------------------------------------------------------------------------
// 好友分组
// ---------------------------------------------------------------------------
export function createFriendGroup(state: DbState, ownerId: string, name: string, now: number): FriendGroupRow {
  const row: FriendGroupRow = { id: nextId(state, 'friendGroup'), ownerId, name, createdAt: now };
  state.friendGroups.set(row.id, row);
  return row;
}

export function renameFriendGroup(state: DbState, ownerId: string, groupId: string, name: string): FriendGroupRow {
  const row = state.friendGroups.get(groupId);
  if (!row || row.ownerId !== ownerId) {
    throw new MockDbError(20005, '分组不存在');
  }
  row.name = name;
  return row;
}

export function deleteFriendGroup(state: DbState, ownerId: string, groupId: string): void {
  const row = state.friendGroups.get(groupId);
  if (!row || row.ownerId !== ownerId) {
    throw new MockDbError(20005, '分组不存在');
  }
  state.friendGroups.delete(groupId);
  // 组内好友回落到默认分组。
  for (const friend of state.friends.values()) {
    if (friend.ownerId === ownerId && friend.groupId === groupId) {
      friend.groupId = '0';
    }
  }
}

export function listFriendGroups(state: DbState, ownerId: string): FriendGroupRow[] {
  return [...state.friendGroups.values()].filter((row) => row.ownerId === ownerId).sort((a, b) => a.createdAt - b.createdAt);
}

// ---------------------------------------------------------------------------
// 黑名单
// ---------------------------------------------------------------------------
export function blockUser(state: DbState, ownerId: string, targetId: string, now: number): void {
  requireUser(state, targetId);
  if (ownerId === targetId) {
    throw new MockDbError(20004, '不能拉黑自己');
  }
  if (!state.blacklist.has(pairKey(ownerId, targetId))) {
    state.blacklist.set(pairKey(ownerId, targetId), { ownerId, blockedId: targetId, createdAt: now });
  }
  // 拉黑即解除好友关系，并取消双方待处理申请。
  state.friends.delete(pairKey(ownerId, targetId));
  state.friends.delete(pairKey(targetId, ownerId));
  for (const request of state.friendRequests.values()) {
    const involved =
      (request.fromUserId === ownerId && request.toUserId === targetId) ||
      (request.fromUserId === targetId && request.toUserId === ownerId);
    if (involved && request.status === 1) {
      request.status = 4;
      request.updatedAt = now;
    }
  }
}

export function unblockUser(state: DbState, ownerId: string, targetId: string): void {
  if (!state.blacklist.delete(pairKey(ownerId, targetId))) {
    throw new MockDbError(20002, '该用户不在黑名单中');
  }
}

export function listBlacklist(state: DbState, ownerId: string): BlacklistRow[] {
  return [...state.blacklist.values()].filter((row) => row.ownerId === ownerId).sort((a, b) => b.createdAt - a.createdAt);
}

// ---------------------------------------------------------------------------
// 通知
// ---------------------------------------------------------------------------
export interface PushNotificationInput {
  userId: string;
  type: 1 | 2 | 3;
  title: string;
  content: string;
  referenceId?: string;
}

export function pushNotification(state: DbState, input: PushNotificationInput, now: number): NotificationRow {
  const row: NotificationRow = {
    id: nextId(state, 'notification'),
    userId: input.userId,
    type: input.type,
    title: input.title,
    content: input.content,
    isRead: false,
    referenceId: input.referenceId ?? '',
    createdAt: now,
  };
  state.notifications.set(row.id, row);
  return row;
}

export function listNotifications(
  state: DbState,
  userId: string,
  filter: { type?: number; isRead?: boolean },
): NotificationRow[] {
  const rows: NotificationRow[] = [];
  for (const row of state.notifications.values()) {
    if (row.userId !== userId) {
      continue;
    }
    if (filter.type !== undefined && filter.type !== 0 && row.type !== filter.type) {
      continue;
    }
    if (filter.isRead !== undefined && row.isRead !== filter.isRead) {
      continue;
    }
    rows.push(row);
  }
  return rows.sort((a, b) => b.createdAt - a.createdAt);
}

export function unreadNotificationCount(state: DbState, userId: string): number {
  let count = 0;
  for (const row of state.notifications.values()) {
    if (row.userId === userId && !row.isRead) {
      count += 1;
    }
  }
  return count;
}

export function markNotificationRead(state: DbState, userId: string, notificationId: string): void {
  const row = state.notifications.get(notificationId);
  if (!row || row.userId !== userId) {
    throw new MockDbError(60001, '通知不存在');
  }
  row.isRead = true;
}

export function markAllNotificationsRead(state: DbState, userId: string): void {
  for (const row of state.notifications.values()) {
    if (row.userId === userId) {
      row.isRead = true;
    }
  }
}

export function deleteNotification(state: DbState, userId: string, notificationId: string): void {
  const row = state.notifications.get(notificationId);
  if (!row || row.userId !== userId) {
    throw new MockDbError(60001, '通知不存在');
  }
  state.notifications.delete(notificationId);
}

/** 好友在线状态：NPC 恒在线的简化模型（realtime hub 会做波动）。 */
export function presenceOf(state: DbState, userId: string): 'online' | 'offline' {
  const user = getUser(state, userId);
  return user?.isNpc ? 'online' : 'offline';
}
