import type { ConversationDTO, ConversationMemberDTO, ConversationSettingsData } from '@/types/Conversation/Conversation';
import { memberKey, nextId, type DbState } from './state';
import { MockDbError, getUser, requireUser } from './users';
import type { ConversationRow, MemberRow } from './schema';

const MAX_MEMBERS = 500;

function createMemberRow(conversationId: string, userId: string, role: 0 | 1 | 2, now: number): MemberRow {
  return {
    conversationId,
    userId,
    role,
    alias: '',
    joinedAt: now,
    lastReadSeq: 0,
    muted: false,
    muteUntilSec: 0,
    pinned: false,
    dnd: false,
    nickname: '',
  };
}

export function getConversation(state: DbState, conversationId: string): ConversationRow | null {
  return state.conversations.get(conversationId) ?? null;
}

export function requireConversation(state: DbState, conversationId: string): ConversationRow {
  const conversation = state.conversations.get(conversationId);
  if (!conversation) {
    throw new MockDbError(30001, '会话不存在');
  }
  return conversation;
}

export function getMember(state: DbState, conversationId: string, userId: string): MemberRow | null {
  return state.members.get(memberKey(conversationId, userId)) ?? null;
}

export function requireMember(state: DbState, conversationId: string, userId: string): MemberRow {
  const member = getMember(state, conversationId, userId);
  if (!member) {
    throw new MockDbError(30004, '非会话成员');
  }
  return member;
}

export function listMembersOf(state: DbState, conversationId: string): MemberRow[] {
  const members: MemberRow[] = [];
  for (const member of state.members.values()) {
    if (member.conversationId === conversationId) {
      members.push(member);
    }
  }
  // 群主 > 管理员 > 成员，同级按加入时间。
  return members.sort((a, b) => {
    const rank = (role: number): number => (role === 1 ? 0 : role === 2 ? 1 : 2);
    return rank(a.role) - rank(b.role) || a.joinedAt - b.joinedAt;
  });
}

export function memberCountOf(state: DbState, conversationId: string): number {
  let count = 0;
  for (const member of state.members.values()) {
    if (member.conversationId === conversationId) {
      count += 1;
    }
  }
  return count;
}

export function listConversationRowsFor(state: DbState, userId: string): ConversationRow[] {
  const rows: ConversationRow[] = [];
  for (const member of state.members.values()) {
    if (member.userId === userId) {
      const conversation = state.conversations.get(member.conversationId);
      if (conversation) {
        rows.push(conversation);
      }
    }
  }
  return rows;
}

/** 查找两人之间已存在的单聊（幂等创建的关键）。 */
export function findDirectConversation(state: DbState, userA: string, userB: string): ConversationRow | null {
  for (const conversation of state.conversations.values()) {
    if (conversation.type !== 1) {
      continue;
    }
    const hasA = state.members.has(memberKey(conversation.id, userA));
    const hasB = state.members.has(memberKey(conversation.id, userB));
    if (hasA && hasB) {
      return conversation;
    }
  }
  return null;
}

export function createDirectConversation(state: DbState, creatorId: string, peerUserId: string, now: number): ConversationRow {
  requireUser(state, peerUserId);
  const existing = findDirectConversation(state, creatorId, peerUserId);
  if (existing) {
    return existing;
  }

  const row: ConversationRow = {
    id: nextId(state, 'conversation'),
    type: 1,
    name: '',
    avatar: '',
    ownerId: '0',
    maxSeq: 0,
    lastMessageId: '0',
    lastMessagePreview: '',
    lastMessageAt: now,
    announcement: '',
    isMutedAll: false,
    createdAt: now,
    updatedAt: now,
  };
  state.conversations.set(row.id, row);
  state.members.set(memberKey(row.id, creatorId), createMemberRow(row.id, creatorId, 0, now));
  state.members.set(memberKey(row.id, peerUserId), createMemberRow(row.id, peerUserId, 0, now));
  return row;
}

export function createGroupConversation(
  state: DbState,
  creatorId: string,
  input: { name: string; avatar?: string; memberIds?: string[] },
  now: number,
): ConversationRow {
  const uniqueMembers = [...new Set((input.memberIds ?? []).filter((id) => id !== creatorId))];
  if (uniqueMembers.length + 1 > MAX_MEMBERS) {
    throw new MockDbError(30008, '成员数超上限 (500)');
  }

  const row: ConversationRow = {
    id: nextId(state, 'conversation'),
    type: 2,
    name: input.name,
    avatar: input.avatar ?? '',
    ownerId: creatorId,
    maxSeq: 0,
    lastMessageId: '0',
    lastMessagePreview: '',
    lastMessageAt: now,
    announcement: '',
    isMutedAll: false,
    createdAt: now,
    updatedAt: now,
  };
  state.conversations.set(row.id, row);
  state.members.set(memberKey(row.id, creatorId), createMemberRow(row.id, creatorId, 1, now));
  for (const memberId of uniqueMembers) {
    if (getUser(state, memberId)) {
      state.members.set(memberKey(row.id, memberId), createMemberRow(row.id, memberId, 0, now));
    }
  }
  return row;
}

function requireManager(state: DbState, conversationId: string, operatorId: string): MemberRow {
  const operator = requireMember(state, conversationId, operatorId);
  if (operator.role !== 1 && operator.role !== 2) {
    throw new MockDbError(30005, '权限不足');
  }
  return operator;
}

export interface AddMembersResult {
  addedUserIds: string[];
  alreadyMemberIds: string[];
}

export function addMembers(
  state: DbState,
  conversationId: string,
  operatorId: string,
  userIds: string[],
  now: number,
): AddMembersResult {
  const conversation = requireConversation(state, conversationId);
  requireManager(state, conversationId, operatorId);

  const added: string[] = [];
  const already: string[] = [];
  for (const userId of new Set(userIds)) {
    if (!getUser(state, userId)) {
      continue;
    }
    if (getMember(state, conversationId, userId)) {
      already.push(userId);
      continue;
    }
    if (memberCountOf(state, conversationId) + 1 > MAX_MEMBERS) {
      throw new MockDbError(30008, '成员数超上限 (500)');
    }
    state.members.set(memberKey(conversationId, userId), createMemberRow(conversationId, userId, 0, now));
    added.push(userId);
  }
  conversation.updatedAt = now;
  return { addedUserIds: added, alreadyMemberIds: already };
}

export function kickMembers(state: DbState, conversationId: string, operatorId: string, userIds: string[], now: number): void {
  const conversation = requireConversation(state, conversationId);
  const operator = requireManager(state, conversationId, operatorId);

  for (const userId of userIds) {
    const target = getMember(state, conversationId, userId);
    if (!target) {
      throw new MockDbError(30003, '用户不在会话中');
    }
    // 管理员不能踢群主 / 其他管理员。
    if (operator.role === 2 && target.role !== 0) {
      throw new MockDbError(30005, '权限不足');
    }
    state.members.delete(memberKey(conversationId, userId));
  }
  conversation.updatedAt = now;
}

export function muteMember(
  state: DbState,
  conversationId: string,
  operatorId: string,
  userId: string,
  durationSeconds: number,
  now: number,
): void {
  const conversation = requireConversation(state, conversationId);
  requireManager(state, conversationId, operatorId);
  const target = getMember(state, conversationId, userId);
  if (!target) {
    throw new MockDbError(30003, '用户不在会话中');
  }
  target.muted = true;
  target.muteUntilSec = durationSeconds > 0 ? Math.floor(now / 1000) + durationSeconds : 0;
  conversation.updatedAt = now;
}

export function unmuteMember(state: DbState, conversationId: string, operatorId: string, userId: string, now: number): void {
  const conversation = requireConversation(state, conversationId);
  requireManager(state, conversationId, operatorId);
  const target = getMember(state, conversationId, userId);
  if (!target) {
    throw new MockDbError(30003, '用户不在会话中');
  }
  target.muted = false;
  target.muteUntilSec = 0;
  conversation.updatedAt = now;
}

/** 消息发送前的禁言校验（含到期自动解除）。 */
export function assertCanSpeak(state: DbState, conversationId: string, userId: string, now: number): void {
  const conversation = requireConversation(state, conversationId);
  const member = requireMember(state, conversationId, userId);
  if (conversation.isMutedAll && member.role === 0) {
    throw new MockDbError(30007, '全员禁言中');
  }
  if (!member.muted) {
    return;
  }
  if (member.muteUntilSec > 0 && member.muteUntilSec * 1000 <= now) {
    member.muted = false;
    member.muteUntilSec = 0;
    return;
  }
  throw new MockDbError(30006, '已被禁言');
}

export function transferOwner(state: DbState, conversationId: string, operatorId: string, newOwnerId: string, now: number): void {
  const conversation = requireConversation(state, conversationId);
  const operator = requireMember(state, conversationId, operatorId);
  if (operator.role !== 1) {
    throw new MockDbError(30005, '权限不足');
  }
  if (operatorId === newOwnerId) {
    throw new MockDbError(30009, '不能转让给自己');
  }
  const target = getMember(state, conversationId, newOwnerId);
  if (!target) {
    throw new MockDbError(30003, '用户不在会话中');
  }
  operator.role = 0;
  target.role = 1;
  conversation.ownerId = newOwnerId;
  conversation.updatedAt = now;
}

export function setAnnouncement(state: DbState, conversationId: string, operatorId: string, content: string, now: number): void {
  const conversation = requireConversation(state, conversationId);
  requireManager(state, conversationId, operatorId);
  conversation.announcement = content;
  conversation.updatedAt = now;
}

export function getSettings(state: DbState, conversationId: string, userId: string): ConversationSettingsData {
  requireConversation(state, conversationId);
  const member = requireMember(state, conversationId, userId);
  // 契约 §5：响应键为 muted/pinned（无 is- 前缀）。
  return { muted: member.dnd, pinned: member.pinned, nickname: member.nickname };
}

export function updateSettings(
  state: DbState,
  conversationId: string,
  userId: string,
  patch: { isMuted?: boolean; isPinned?: boolean; nickname?: string },
): void {
  requireConversation(state, conversationId);
  const member = requireMember(state, conversationId, userId);
  if (patch.isMuted !== undefined) member.dnd = patch.isMuted;
  if (patch.isPinned !== undefined) member.pinned = patch.isPinned;
  if (patch.nickname !== undefined) member.nickname = patch.nickname;
}

export function markRead(state: DbState, conversationId: string, userId: string, seq: number): void {
  requireConversation(state, conversationId);
  const member = requireMember(state, conversationId, userId);
  member.lastReadSeq = Math.max(member.lastReadSeq, Math.floor(seq));
}

/** 单聊取对端成员（展示名/头像/在线态都来自对端）。 */
export function directPeerOf(state: DbState, conversationId: string, selfId: string): MemberRow | null {
  for (const member of state.members.values()) {
    if (member.conversationId === conversationId && member.userId !== selfId) {
      return member;
    }
  }
  return null;
}

export function toConversationDTO(state: DbState, row: ConversationRow, forUserId: string): ConversationDTO {
  const member = getMember(state, row.id, forUserId);
  let name = row.name;
  let avatar = row.avatar;
  if (row.type === 1) {
    const peer = directPeerOf(state, row.id, forUserId);
    const peerUser = peer ? getUser(state, peer.userId) : null;
    name = peerUser?.username ?? '已注销用户';
    avatar = peerUser?.avatar ?? '';
  }
  return {
    id: row.id,
    type: row.type,
    name,
    avatar,
    ownerId: row.ownerId,
    memberCount: memberCountOf(state, row.id),
    maxSeq: row.maxSeq,
    lastMessageId: row.lastMessageId,
    lastMessagePreview: row.lastMessagePreview,
    announcement: row.announcement,
    isMutedAll: row.isMutedAll,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    unreadCount: member ? Math.max(0, row.maxSeq - member.lastReadSeq) : 0,
  };
}

export function toMemberDTO(state: DbState, member: MemberRow): ConversationMemberDTO {
  const user = getUser(state, member.userId);
  return {
    userId: member.userId,
    username: user?.username ?? '已注销用户',
    avatar: user?.avatar ?? '',
    role: member.role,
    alias: member.nickname || member.alias,
    joinedAt: member.joinedAt,
    lastReadSeq: member.lastReadSeq,
    isMuted: member.muted,
    muteUntil: member.muteUntilSec,
    memberType: 1,
    botId: '0',
  };
}
