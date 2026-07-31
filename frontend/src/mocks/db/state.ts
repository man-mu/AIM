import { DB_SCHEMA_VERSION, ID_BASES, type DbSnapshot, type IdKind } from './schema';
import type {
  BlacklistRow,
  ConversationRow,
  FileRow,
  FriendGroupRow,
  FriendRequestRow,
  FriendRow,
  MemberRow,
  MessageRow,
  NotificationRow,
  UserRow,
} from './schema';

/**
 * Mock 数据库内核：纯内存表 + 索引 + 快照序列化。
 * 领域操作按模块拆分（users / conversations / messages / social / files），
 * 本文件只负责数据结构与 id 发号。
 */
export interface DbState {
  users: Map<string, UserRow>;
  conversations: Map<string, ConversationRow>;
  /** key: `${conversationId}:${userId}` */
  members: Map<string, MemberRow>;
  messages: Map<string, MessageRow>;
  /** 会话内消息 id 列表（按 seq 升序），查询索引。 */
  messagesByConv: Map<string, string[]>;
  /** key: `${ownerId}:${friendId}` */
  friends: Map<string, FriendRow>;
  friendRequests: Map<string, FriendRequestRow>;
  friendGroups: Map<string, FriendGroupRow>;
  /** key: `${ownerId}:${blockedId}` */
  blacklist: Map<string, BlacklistRow>;
  notifications: Map<string, NotificationRow>;
  files: Map<string, FileRow>;
  counters: Map<IdKind, bigint>;
}

export function memberKey(conversationId: string, userId: string): string {
  return `${conversationId}:${userId}`;
}

export function pairKey(a: string, b: string): string {
  return `${a}:${b}`;
}

export function createEmptyState(): DbState {
  return {
    users: new Map(),
    conversations: new Map(),
    members: new Map(),
    messages: new Map(),
    messagesByConv: new Map(),
    friends: new Map(),
    friendRequests: new Map(),
    friendGroups: new Map(),
    blacklist: new Map(),
    notifications: new Map(),
    files: new Map(),
    counters: new Map(),
  };
}

export function nextId(state: DbState, kind: IdKind): string {
  const current = state.counters.get(kind) ?? ID_BASES[kind];
  const next = current + 1n;
  state.counters.set(kind, next);
  return next.toString();
}

export function serialize(state: DbState): DbSnapshot {
  return {
    version: DB_SCHEMA_VERSION,
    users: [...state.users.values()],
    conversations: [...state.conversations.values()],
    members: [...state.members.values()],
    messages: [...state.messages.values()],
    friends: [...state.friends.values()],
    friendRequests: [...state.friendRequests.values()],
    friendGroups: [...state.friendGroups.values()],
    blacklist: [...state.blacklist.values()],
    notifications: [...state.notifications.values()],
    files: [...state.files.values()],
    counters: Object.fromEntries([...state.counters.entries()].map(([key, value]) => [key, value.toString()])),
  };
}

export function hydrate(snapshot: DbSnapshot): DbState | null {
  if (snapshot.version !== DB_SCHEMA_VERSION) {
    return null;
  }

  const state = createEmptyState();
  for (const row of snapshot.users) state.users.set(row.id, row);
  for (const row of snapshot.conversations) state.conversations.set(row.id, row);
  for (const row of snapshot.members) state.members.set(memberKey(row.conversationId, row.userId), row);
  for (const row of snapshot.friends) state.friends.set(pairKey(row.ownerId, row.friendId), row);
  for (const row of snapshot.friendRequests) state.friendRequests.set(row.id, row);
  for (const row of snapshot.friendGroups) state.friendGroups.set(row.id, row);
  for (const row of snapshot.blacklist) state.blacklist.set(pairKey(row.ownerId, row.blockedId), row);
  for (const row of snapshot.notifications) state.notifications.set(row.id, row);
  for (const row of snapshot.files) state.files.set(row.fileId, row);
  for (const [kind, value] of Object.entries(snapshot.counters)) {
    state.counters.set(kind as IdKind, BigInt(value));
  }

  // 消息按 seq 排序后重建索引。
  const sorted = [...snapshot.messages].sort((a, b) => a.seq - b.seq);
  for (const row of sorted) {
    state.messages.set(row.id, row);
    const bucket = state.messagesByConv.get(row.conversationId);
    if (bucket) {
      bucket.push(row.id);
    } else {
      state.messagesByConv.set(row.conversationId, [row.id]);
    }
  }
  return state;
}
