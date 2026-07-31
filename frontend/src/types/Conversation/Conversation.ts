import type { Int64 } from '../User/User';

/** 会话类型：1=单聊 2=群聊（对齐后端 int 编码）。 */
export type ConversationType = 1 | 2;

/** 成员角色：0=成员 1=群主 2=管理员。 */
export type MemberRole = 0 | 1 | 2;

/** ConversationDTO（api-v1.md 附录 B）。 */
export interface ConversationDTO {
  id: Int64;
  type: ConversationType;
  name: string;
  avatar: string;
  ownerId: Int64;
  memberCount: number;
  maxSeq: number;
  lastMessageId: Int64;
  lastMessagePreview: string;
  announcement: string;
  isMutedAll: boolean;
  createdAt: number;
  updatedAt: number;
  unreadCount: number;
}

/** ConversationMemberDTO（附录 D）。 */
export interface ConversationMemberDTO {
  userId: Int64;
  username: string;
  avatar: string;
  role: MemberRole;
  alias: string;
  joinedAt: number;
  lastReadSeq: number;
  isMuted: boolean;
  /** 禁言截止（epoch 秒；0=未禁言或永久禁言）。 */
  muteUntil: number;
  memberType: number;
  botId: Int64;
}

// ---- 请求 / 响应 ----

export interface CreateConversationParams {
  type: ConversationType;
  peerUserId?: Int64;
  name?: string;
  avatar?: string;
  memberIds?: Int64[];
}

export interface CreateConversationData {
  conversationId: Int64;
  conversation: ConversationDTO;
}

export interface ListConversationsParams {
  pageNum?: number;
  pageSize?: number;
}

export interface ListConversationsData {
  conversations: ConversationDTO[];
  total: number;
}

export interface GetMembersData {
  members: ConversationMemberDTO[];
  total: number;
}

export interface InviteMembersData {
  addedUserIds: Int64[];
  alreadyMemberIds: Int64[];
}

/** GET /convs/:id/settings 响应（契约 §5：键名无 is- 前缀）。 */
export interface ConversationSettingsData {
  muted: boolean;
  pinned: boolean;
  nickname: string;
}

/** PUT /convs/:id/settings 请求体（契约 §5：仍为 isMuted/isPinned，null 不更新）。 */
export interface UpdateSettingsParams {
  isMuted?: boolean;
  isPinned?: boolean;
  nickname?: string;
}
