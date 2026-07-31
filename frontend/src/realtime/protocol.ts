import type { MessageContent, MessageDTO, MsgType } from '@/types/Message/Message';
import type { NotificationDTO } from '@/types/Notification/Notification';
import type { Int64 } from '@/types/User/User';

/**
 * 实时下行事件协议（api-v1.md §10.4 / §10.5）。
 *
 * 该文件是「传输无关」的：MockRealtimeHub 与将来的 wsChannel
 * 都生产同一套事件对象，dispatcher 消费时无需关心来源。
 */
export interface RealtimeFrame<E extends string = string, D = unknown> {
  event: E;
  data: D;
  timestamp: number;
}

export interface MessageNewData {
  messageId: Int64;
  convId: Int64;
  seq: number;
  fromUserId: Int64;
  msgType: MsgType;
  status: number;
  content: MessageContent;
  replyToId: Int64;
  replyToPreview: string;
  createdAt: number;
  /** 当前用户在该会话的未读数（服务端按人计算）。 */
  unreadCount: number;
  senderInfo: {
    id: Int64;
    username: string;
    avatar: string;
  };
}

export type DownstreamEvent =
  | { event: 'pong'; data: Record<string, never> }
  | { event: 'message.new'; data: MessageNewData }
  | { event: 'message.recalled'; data: { messageId: Int64; convId: Int64; userId: Int64 } }
  | { event: 'message.edited'; data: { messageId: Int64; convId: Int64; userId: Int64; newContent: MessageContent } }
  | { event: 'presence'; data: { userId: Int64; status: 'online' | 'offline' } }
  | { event: 'read_sync'; data: { convId: Int64; userId: Int64; lastReadSeq: number } }
  // 契约 §9.2：下行输入中通知为 typing.notify（与上行 typing 区分）。
  | { event: 'typing.notify'; data: { convId: Int64; userId: Int64 } }
  | { event: 'typing.stop'; data: { convId: Int64; userId: Int64 } }
  | { event: 'unread_count'; data: { convId: Int64; count: number } }
  | { event: 'read_receipt'; data: { messageId: Int64; userId: Int64; readAt: number } }
  // —— 以下为前端扩展事件（mock 阶段使用；真实后端上线后由 signaling 决定是否保留）——
  | { event: 'conversation.updated'; data: { convId: Int64 } }
  | { event: 'notification.new'; data: NotificationDTO };

export type DownstreamEventName = DownstreamEvent['event'];

export type UpstreamEvent =
  | { event: 'ping'; data: Record<string, never> }
  | { event: 'subscribe_presence'; data: { userIds: Int64[] } }
  | { event: 'unsubscribe_presence'; data: { userIds: Int64[] } }
  | { event: 'typing'; data: { convId: Int64; userId: Int64 } }
  | { event: 'typing_stop'; data: { convId: Int64; userId: Int64 } }
  | { event: 'ack'; data: { messageId: Int64; convId: Int64; seq: number } };

/** 由 MessageDTO 组装 message.new 事件体。 */
export function toMessageNewData(
  message: MessageDTO,
  sender: { id: Int64; username: string; avatar: string },
  unreadCount: number,
): MessageNewData {
  return {
    messageId: message.messageId,
    convId: message.conversationId,
    seq: message.seq,
    fromUserId: message.fromUserId,
    msgType: message.msgType,
    status: message.status,
    content: message.content,
    replyToId: message.replyToId,
    replyToPreview: message.replyToPreview,
    createdAt: message.createdAt,
    unreadCount,
    senderInfo: sender,
  };
}
