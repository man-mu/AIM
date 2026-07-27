import { toInt64String } from '@/lib/ids';
import type { MessageContent, MessageDTO, MsgType } from '@/types/Message/Message';

/** 发送状态：sent=服务端已确认；sending/failed 仅存在于本地乐观层。 */
export type SendState = 'sent' | 'sending' | 'failed';

export interface UiMessage {
  /** 服务端消息 id；乐观占位时为 clientMsgId。 */
  id: string;
  clientMsgId: string | null;
  conversationId: string;
  /** 乐观占位时为 0（尚未分配）。 */
  seq: number;
  senderId: string;
  msgType: MsgType;
  /** 1 正常 2 已撤回 3 已删除。 */
  status: number;
  content: MessageContent;
  replyToId: string;
  replyToPreview: string;
  editedAt: number;
  createdAt: number;
  sendState: SendState;
  /** 附件上传进度（0~100，仅乐观占位期存在）。 */
  progress?: number;
}

export function mapMessage(dto: MessageDTO): UiMessage {
  return {
    id: toInt64String(dto.messageId),
    clientMsgId: null,
    conversationId: toInt64String(dto.conversationId),
    seq: dto.seq,
    senderId: toInt64String(dto.fromUserId),
    msgType: dto.msgType,
    status: dto.status,
    content: dto.content,
    replyToId: toInt64String(dto.replyToId),
    replyToPreview: dto.replyToPreview,
    editedAt: dto.editedAt,
    createdAt: dto.createdAt,
    sendState: 'sent',
  };
}

export interface PendingDraft {
  conversationId: string;
  senderId: string;
  msgType: MsgType;
  content: MessageContent;
  replyToId?: string;
  replyToPreview?: string;
  clientMsgId: string;
  createdAt: number;
}

/** 乐观占位消息。 */
export function createPendingMessage(draft: PendingDraft): UiMessage {
  return {
    id: draft.clientMsgId,
    clientMsgId: draft.clientMsgId,
    conversationId: draft.conversationId,
    seq: 0,
    senderId: draft.senderId,
    msgType: draft.msgType,
    status: 1,
    content: draft.content,
    replyToId: draft.replyToId ?? '0',
    replyToPreview: draft.replyToPreview ?? '',
    editedAt: 0,
    createdAt: draft.createdAt,
    sendState: 'sending',
  };
}

/** 会话内消息预览文案（发送侧本地更新会话列表用）。 */
export function previewOfContent(msgType: MsgType, content: MessageContent): string {
  switch (msgType) {
    case 1:
      return (content as { text?: string }).text ?? '';
    case 2:
      return '[图片]';
    case 3: {
      const name = (content as { name?: string }).name;
      return name ? `[文件] ${name}` : '[文件]';
    }
    case 7:
      return (content as { detail?: string }).detail ?? '[系统消息]';
    default:
      return '[消息]';
  }
}
