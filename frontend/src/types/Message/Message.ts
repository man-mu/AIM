import type { Int64 } from '../User/User';

/**
 * 消息类型（api-v1.md §6.1）：1 文本 2 图片 3 文件 4 视频 5 语音 6 位置 7 系统。
 * Phase A 前端实现 1 / 2 / 3 / 7，其余类型渲染占位气泡。
 */
export type MsgType = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** 消息状态：1=正常 2=已撤回 3=已删除。 */
export type MessageStatus = 1 | 2 | 3;

export interface TextContent {
  text: string;
  mentionUserIds?: Int64[];
  mentionAll?: boolean;
}

export interface ImageContent {
  fileId: Int64;
  url: string;
  thumbnailUrl: string;
  width: number;
  height: number;
  size: number;
  format: string;
}

export interface FileContent {
  fileId: Int64;
  url: string;
  name: string;
  size: number;
  ext: string;
  mimeType: string;
}

export interface SystemContent {
  action: string;
  detail: string;
  relatedUserIds?: Int64[];
  actorId?: Int64;
  actorType?: string;
  payload?: string;
}

export type MessageContent = TextContent | ImageContent | FileContent | SystemContent | Record<string, unknown>;

/** MessageDTO（api-v1.md §6.6 list 项）。 */
export interface MessageDTO {
  messageId: Int64;
  conversationId: Int64;
  seq: number;
  fromUserId: Int64;
  msgType: MsgType;
  status: MessageStatus;
  content: MessageContent;
  replyToId: Int64;
  replyToPreview: string;
  editCount: number;
  editedAt: number;
  createdAt: number;
}

// ---- 请求 / 响应 ----

export interface SendMessageParams {
  conversationId: Int64;
  msgType: MsgType;
  content: MessageContent;
  replyToId?: Int64;
  clientMsgId: string;
}

export interface SendMessageData {
  messageId: Int64;
  seq: number;
  createdAt: number;
}

export interface ListMessagesParams {
  cursor: string;
  limit?: number;
}

export interface ListMessagesData {
  list: MessageDTO[];
  nextCursor: string | null;
  hasMore: boolean;
  total: number;
}

export interface SyncMessagesData {
  list: MessageDTO[];
  hasMore: boolean;
  maxSeq: number;
}

export interface EditMessageParams {
  newContent: MessageContent;
}
