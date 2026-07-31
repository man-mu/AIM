import { toInt64String } from '@/lib/ids';
import type {
  ConversationDTO,
  ConversationMemberDTO,
  ConversationSettingsData,
} from '@/types/Conversation/Conversation';

/**
 * UI 模型：DTO 大数 id 归一为 string，settings 合并进会话对象，
 * 组件层不再接触 wire 形态。
 */
export interface UiConversation {
  id: string;
  type: 'direct' | 'group';
  name: string;
  avatar: string;
  ownerId: string;
  memberCount: number;
  maxSeq: number;
  lastMessagePreview: string;
  /** 排序时间：取会话 updatedAt（最新消息会推动它）。 */
  lastActiveAt: number;
  announcement: string;
  isMutedAll: boolean;
  unreadCount: number;
  isPinned: boolean;
  /** 免打扰。 */
  isDnd: boolean;
  nickname: string;
  createdAt: number;
}

export interface UiMember {
  userId: string;
  username: string;
  avatar: string;
  role: 0 | 1 | 2;
  displayName: string;
  joinedAt: number;
  lastReadSeq: number;
  isMuted: boolean;
  muteUntilSec: number;
}

export function mapConversation(dto: ConversationDTO, settings?: ConversationSettingsData | null): UiConversation {
  return {
    id: toInt64String(dto.id),
    type: dto.type === 1 ? 'direct' : 'group',
    name: dto.name,
    avatar: dto.avatar,
    ownerId: toInt64String(dto.ownerId),
    memberCount: dto.memberCount,
    maxSeq: dto.maxSeq,
    lastMessagePreview: dto.lastMessagePreview,
    lastActiveAt: dto.updatedAt,
    announcement: dto.announcement,
    isMutedAll: dto.isMutedAll,
    unreadCount: dto.unreadCount,
    isPinned: settings?.pinned ?? false,
    isDnd: settings?.muted ?? false,
    nickname: settings?.nickname ?? '',
    createdAt: dto.createdAt,
  };
}

export function mapMember(dto: ConversationMemberDTO): UiMember {
  return {
    userId: toInt64String(dto.userId),
    username: dto.username,
    avatar: dto.avatar,
    role: dto.role,
    displayName: dto.alias || dto.username,
    joinedAt: dto.joinedAt,
    lastReadSeq: dto.lastReadSeq,
    isMuted: dto.isMuted,
    muteUntilSec: dto.muteUntil,
  };
}

/** 置顶优先，其余按最近活跃倒序。 */
export function sortConversations(list: UiConversation[]): UiConversation[] {
  return [...list].sort((a, b) => {
    if (a.isPinned !== b.isPinned) {
      return a.isPinned ? -1 : 1;
    }
    return b.lastActiveAt - a.lastActiveAt;
  });
}

/** 徽标总未读：免打扰会话不计入。 */
export function totalUnread(list: UiConversation[]): number {
  return list.reduce((sum, conversation) => sum + (conversation.isDnd ? 0 : conversation.unreadCount), 0);
}

export const ROLE_LABELS: Record<UiMember['role'], string> = {
  0: '成员',
  1: '群主',
  2: '管理员',
};
