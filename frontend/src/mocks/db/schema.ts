import type { MessageContent, MessageStatus, MsgType } from '@/types/Message/Message';

/**
 * Mock 数据库行结构。
 *
 * 约定：
 * - 所有 id 为十进制字符串（模拟 Java long 经 json-bigint 的形态）；
 * - 时间戳一律 epoch 毫秒（muteUntilSec 例外，对齐后端的秒级字段）；
 * - 行对象只存数据不含方法，方便 structuredClone / JSON 快照。
 */
export interface UserRow {
  id: string;
  username: string;
  /** mock 环境明文即可。 */
  password: string;
  phone: string;
  email: string;
  avatar: string;
  gender: 0 | 1 | 2;
  bio: string;
  birthday: number;
  createdAt: number;
  updatedAt: number;
  balance: string;
  disabled: boolean;
  /** 是否为剧本 NPC（用于模拟实时活动）。 */
  isNpc: boolean;
}

export interface ConversationRow {
  id: string;
  type: 1 | 2;
  name: string;
  avatar: string;
  ownerId: string;
  maxSeq: number;
  lastMessageId: string;
  lastMessagePreview: string;
  lastMessageAt: number;
  announcement: string;
  isMutedAll: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface MemberRow {
  conversationId: string;
  userId: string;
  role: 0 | 1 | 2;
  alias: string;
  joinedAt: number;
  lastReadSeq: number;
  /** 管理员禁言。 */
  muted: boolean;
  /** 禁言截止（epoch 秒，0 = 未禁言或永久）。 */
  muteUntilSec: number;
  // —— 当前用户个人设置（settings 接口）——
  pinned: boolean;
  /** 免打扰。 */
  dnd: boolean;
  nickname: string;
}

export interface MessageRow {
  id: string;
  conversationId: string;
  seq: number;
  fromUserId: string;
  clientMsgId: string;
  msgType: MsgType;
  status: MessageStatus;
  content: MessageContent;
  replyToId: string;
  replyToPreview: string;
  editCount: number;
  editedAt: number;
  createdAt: number;
  /** 仅对这些用户隐藏（deleteForMe）。 */
  deletedFor: string[];
}

export interface FriendRow {
  ownerId: string;
  friendId: string;
  remark: string;
  groupId: string;
  createdAt: number;
}

export interface FriendRequestRow {
  id: string;
  fromUserId: string;
  toUserId: string;
  message: string;
  status: 1 | 2 | 3 | 4;
  createdAt: number;
  updatedAt: number;
}

export interface FriendGroupRow {
  id: string;
  ownerId: string;
  name: string;
  createdAt: number;
}

export interface BlacklistRow {
  ownerId: string;
  blockedId: string;
  createdAt: number;
}

export interface NotificationRow {
  id: string;
  userId: string;
  type: 1 | 2 | 3;
  title: string;
  content: string;
  isRead: boolean;
  referenceId: string;
  createdAt: number;
}

export interface FileRow {
  fileId: string;
  name: string;
  key: string;
  size: number;
  mimeType: string;
  ext: string;
  width: number;
  height: number;
  duration: number;
  md5: string;
  purpose: 1 | 2;
  access: 1 | 2 | 3;
  uploaderId: string;
  bucket: string;
  status: 0 | 1 | 2;
  createdAt: number;
}

export interface DbSnapshot {
  version: number;
  users: UserRow[];
  conversations: ConversationRow[];
  members: MemberRow[];
  messages: MessageRow[];
  friends: FriendRow[];
  friendRequests: FriendRequestRow[];
  friendGroups: FriendGroupRow[];
  blacklist: BlacklistRow[];
  notifications: NotificationRow[];
  files: FileRow[];
  counters: Record<string, string>;
}

export const DB_SCHEMA_VERSION = 1;

/** id 段基数：模拟后端不同服务的 id 形态（全部超过 2^53，锻炼大数处理路径）。 */
export const ID_BASES = {
  user: 339394874048512000n,
  conversation: 555000000000000000n,
  message: 888000000000000000n,
  friendRequest: 987650000000000000n,
  friendGroup: 444000000000000000n,
  notification: 777000000000000000n,
  file: 666000000000000000n,
} as const;

export type IdKind = keyof typeof ID_BASES;
