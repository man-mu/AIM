import type { MessageContent, MessageDTO, MsgType, TextContent } from '@/types/Message/Message';
import { assertCanSpeak, requireConversation, requireMember } from './conversations';
import { nextId, type DbState } from './state';
import { MockDbError } from './users';
import type { MessageRow } from './schema';

/** 撤回 / 编辑时间窗（api-v1.md §6.3/§6.4）。 */
export const RECALL_WINDOW_MS = 120_000;
export const EDIT_WINDOW_MS = 120_000;

export function previewOf(msgType: MsgType, content: MessageContent, status: number): string {
  if (status === 2) {
    return '撤回了一条消息';
  }
  switch (msgType) {
    case 1:
      return (content as TextContent).text ?? '';
    case 2:
      return '[图片]';
    case 3: {
      const name = (content as { name?: string }).name;
      return name ? `[文件] ${name}` : '[文件]';
    }
    case 4:
      return '[视频]';
    case 5:
      return '[语音]';
    case 6:
      return '[位置]';
    case 7:
      return (content as { detail?: string }).detail ?? '[系统消息]';
    default:
      return '[消息]';
  }
}

export interface AppendMessageInput {
  conversationId: string;
  fromUserId: string;
  msgType: MsgType;
  content: MessageContent;
  clientMsgId: string;
  replyToId?: string;
  /** 种子数据用：指定创建时间。 */
  createdAt?: number;
  /** 种子/系统消息跳过禁言校验。 */
  skipGuards?: boolean;
}

export function findByClientMsgId(state: DbState, conversationId: string, clientMsgId: string): MessageRow | null {
  const ids = state.messagesByConv.get(conversationId) ?? [];
  for (const id of ids) {
    const row = state.messages.get(id);
    if (row && row.clientMsgId === clientMsgId) {
      return row;
    }
  }
  return null;
}

export function appendMessage(state: DbState, input: AppendMessageInput, now: number): MessageRow {
  const conversation = requireConversation(state, input.conversationId);
  if (!input.skipGuards) {
    assertCanSpeak(state, input.conversationId, input.fromUserId, now);
    const duplicate = findByClientMsgId(state, input.conversationId, input.clientMsgId);
    if (duplicate) {
      throw new MockDbError(40004, '消息重复发送');
    }
  }

  let replyToPreview = '';
  if (input.replyToId && input.replyToId !== '0') {
    const target = state.messages.get(input.replyToId);
    if (target) {
      replyToPreview = previewOf(target.msgType, target.content, target.status);
    }
  }

  const createdAt = input.createdAt ?? now;
  const row: MessageRow = {
    id: nextId(state, 'message'),
    conversationId: input.conversationId,
    seq: conversation.maxSeq + 1,
    fromUserId: input.fromUserId,
    clientMsgId: input.clientMsgId,
    msgType: input.msgType,
    status: 1,
    content: input.content,
    replyToId: input.replyToId ?? '0',
    replyToPreview,
    editCount: 0,
    editedAt: 0,
    createdAt,
    deletedFor: [],
  };

  state.messages.set(row.id, row);
  const bucket = state.messagesByConv.get(row.conversationId);
  if (bucket) {
    bucket.push(row.id);
  } else {
    state.messagesByConv.set(row.conversationId, [row.id]);
  }

  conversation.maxSeq = row.seq;
  conversation.lastMessageId = row.id;
  conversation.lastMessagePreview = previewOf(row.msgType, row.content, row.status);
  conversation.lastMessageAt = createdAt;
  conversation.updatedAt = createdAt;
  return row;
}

export interface ListMessagesResult {
  list: MessageRow[];
  nextCursor: string | null;
  hasMore: boolean;
  total: number;
}

/**
 * 游标分页（seq 降序，cursor=上一页最后一条的 seq，0 表示从最新开始）。
 * 对 userId 隐藏其 deleteForMe 的消息。
 */
export function listMessages(
  state: DbState,
  conversationId: string,
  userId: string,
  cursorSeq: number,
  limit: number,
): ListMessagesResult {
  requireConversation(state, conversationId);
  requireMember(state, conversationId, userId);

  const ids = state.messagesByConv.get(conversationId) ?? [];
  const visible: MessageRow[] = [];
  for (let i = ids.length - 1; i >= 0; i -= 1) {
    const row = state.messages.get(ids[i] as string);
    if (!row || row.deletedFor.includes(userId) || row.status === 3) {
      continue;
    }
    if (cursorSeq > 0 && row.seq >= cursorSeq) {
      continue;
    }
    visible.push(row);
    if (visible.length > limit) {
      break;
    }
  }

  const hasMore = visible.length > limit;
  const page = visible.slice(0, limit);
  const last = page[page.length - 1];
  return {
    list: page,
    nextCursor: hasMore && last ? String(last.seq) : null,
    hasMore,
    total: ids.length,
  };
}

export interface SyncMessagesResult {
  list: MessageRow[];
  hasMore: boolean;
  maxSeq: number;
}

/** 增量同步：拉取 seq > fromSeq 的消息（升序）。 */
export function syncMessages(
  state: DbState,
  conversationId: string,
  userId: string,
  fromSeq: number,
  limit: number,
): SyncMessagesResult {
  const conversation = requireConversation(state, conversationId);
  requireMember(state, conversationId, userId);

  const ids = state.messagesByConv.get(conversationId) ?? [];
  const list: MessageRow[] = [];
  for (const id of ids) {
    const row = state.messages.get(id);
    if (!row || row.seq <= fromSeq || row.deletedFor.includes(userId) || row.status === 3) {
      continue;
    }
    list.push(row);
    if (list.length > limit) {
      break;
    }
  }
  const hasMore = list.length > limit;
  return { list: list.slice(0, limit), hasMore, maxSeq: conversation.maxSeq };
}

export function requireMessage(state: DbState, messageId: string): MessageRow {
  const row = state.messages.get(messageId);
  if (!row) {
    throw new MockDbError(40001, '消息不存在');
  }
  return row;
}

export function recallMessage(state: DbState, messageId: string, operatorId: string, now: number): MessageRow {
  const row = requireMessage(state, messageId);
  const operator = requireMember(state, row.conversationId, operatorId);
  const isManager = operator.role === 1 || operator.role === 2;

  if (row.fromUserId !== operatorId && !isManager) {
    throw new MockDbError(40005, '无权操作该消息');
  }
  if (row.fromUserId === operatorId && !isManager && now - row.createdAt > RECALL_WINDOW_MS) {
    throw new MockDbError(40002, '超过可撤回时间');
  }

  row.status = 2;
  syncConversationPreview(state, row);
  return row;
}

export function editMessage(state: DbState, messageId: string, operatorId: string, newContent: MessageContent, now: number): MessageRow {
  const row = requireMessage(state, messageId);
  if (row.fromUserId !== operatorId) {
    throw new MockDbError(40005, '无权操作该消息');
  }
  if (row.status !== 1) {
    throw new MockDbError(40001, '消息不存在');
  }
  if (now - row.createdAt > EDIT_WINDOW_MS) {
    throw new MockDbError(40003, '超过可编辑时间');
  }

  row.content = newContent;
  row.editCount += 1;
  row.editedAt = now;
  syncConversationPreview(state, row);
  return row;
}

export function deleteMessage(state: DbState, messageId: string, operatorId: string, deleteForAll: boolean): MessageRow {
  const row = requireMessage(state, messageId);
  const operator = requireMember(state, row.conversationId, operatorId);

  if (deleteForAll) {
    const isManager = operator.role === 1 || operator.role === 2;
    if (row.fromUserId !== operatorId && !isManager) {
      throw new MockDbError(40005, '无权操作该消息');
    }
    row.status = 3;
  } else if (!row.deletedFor.includes(operatorId)) {
    row.deletedFor.push(operatorId);
  }
  return row;
}

/** 撤回/编辑最后一条消息后，会话预览同步更新。 */
function syncConversationPreview(state: DbState, row: MessageRow): void {
  const conversation = state.conversations.get(row.conversationId);
  if (conversation && conversation.lastMessageId === row.id) {
    conversation.lastMessagePreview = previewOf(row.msgType, row.content, row.status);
  }
}

export function toMessageDTO(row: MessageRow): MessageDTO {
  return {
    messageId: row.id,
    conversationId: row.conversationId,
    seq: row.seq,
    fromUserId: row.fromUserId,
    msgType: row.msgType,
    status: row.status,
    // 撤回消息不下发原内容（对齐真实后端行为）。
    content: row.status === 2 ? {} : row.content,
    replyToId: row.replyToId,
    replyToPreview: row.replyToPreview,
    editCount: row.editCount,
    editedAt: row.editedAt,
    createdAt: row.createdAt,
  };
}
