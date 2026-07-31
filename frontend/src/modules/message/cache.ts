import type { InfiniteData } from '@tanstack/react-query';
import type { ListMessagesData, MessageContent, MessageDTO } from '@/types/Message/Message';
import { mapMessage, type UiMessage } from './model';
import { toInt64String } from '@/lib/ids';

/**
 * 消息分页缓存（InfiniteData<ListMessagesData>）的纯更新函数。
 *
 * 缓存形态约定：pages[0] 是最新一页（cursor=0，seq 降序）；
 * 向上翻页 append 更早的页。渲染层用 flattenAscending 得到升序列表。
 */
export type MessagesCache = InfiniteData<ListMessagesData, string>;

function dtoId(dto: MessageDTO): string {
  return toInt64String(dto.messageId);
}

export function containsMessage(cache: MessagesCache | undefined, messageId: string): boolean {
  if (!cache) {
    return false;
  }
  return cache.pages.some((page) => page.list.some((dto) => dtoId(dto) === messageId));
}

/** 实时新消息进入最新页头部（seq 降序），带去重。 */
export function appendIncoming(cache: MessagesCache | undefined, dto: MessageDTO): MessagesCache | undefined {
  if (!cache || cache.pages.length === 0) {
    return cache;
  }
  if (containsMessage(cache, dtoId(dto))) {
    return cache;
  }
  const [first, ...rest] = cache.pages;
  const nextFirst: ListMessagesData = {
    ...(first as ListMessagesData),
    list: [dto, ...(first as ListMessagesData).list],
    total: ((first as ListMessagesData).total ?? 0) + 1,
  };
  return { ...cache, pages: [nextFirst, ...rest] };
}

function mapMessageInCache(
  cache: MessagesCache | undefined,
  messageId: string,
  transform: (dto: MessageDTO) => MessageDTO,
): MessagesCache | undefined {
  if (!cache) {
    return cache;
  }
  return {
    ...cache,
    pages: cache.pages.map((page) => ({
      ...page,
      list: page.list.map((dto) => (dtoId(dto) === messageId ? transform(dto) : dto)),
    })),
  };
}

export function applyRecalled(cache: MessagesCache | undefined, messageId: string): MessagesCache | undefined {
  return mapMessageInCache(cache, messageId, (dto) => ({ ...dto, status: 2, content: {} }));
}

export function applyEdited(
  cache: MessagesCache | undefined,
  messageId: string,
  newContent: MessageContent,
  editedAt: number,
): MessagesCache | undefined {
  return mapMessageInCache(cache, messageId, (dto) => ({
    ...dto,
    content: newContent,
    editCount: dto.editCount + 1,
    editedAt,
  }));
}

export function removeMessage(cache: MessagesCache | undefined, messageId: string): MessagesCache | undefined {
  if (!cache) {
    return cache;
  }
  return {
    ...cache,
    pages: cache.pages.map((page) => ({
      ...page,
      list: page.list.filter((dto) => dtoId(dto) !== messageId),
    })),
  };
}

/**
 * 渲染列表：分页(降序)拍平为升序 + 追加本地乐观消息。
 * 乐观消息若已被服务端页覆盖（同 clientMsgId 出现），由调用侧负责剔除。
 */
export function flattenAscending(cache: MessagesCache | undefined, pending: UiMessage[]): UiMessage[] {
  const fromServer: UiMessage[] = [];
  if (cache) {
    for (let pageIndex = cache.pages.length - 1; pageIndex >= 0; pageIndex -= 1) {
      const page = cache.pages[pageIndex] as ListMessagesData;
      for (let i = page.list.length - 1; i >= 0; i -= 1) {
        fromServer.push(mapMessage(page.list[i] as MessageDTO));
      }
    }
  }
  return pending.length === 0 ? fromServer : [...fromServer, ...pending];
}

/** 服务端确认后，把乐观消息升级为正式消息所需的 DTO。 */
export function dtoFromAck(
  pending: UiMessage,
  ack: { messageId: string | number; seq: number; createdAt: number },
): MessageDTO {
  return {
    messageId: toInt64String(ack.messageId),
    conversationId: pending.conversationId,
    seq: ack.seq,
    fromUserId: pending.senderId,
    msgType: pending.msgType,
    status: 1,
    content: pending.content,
    replyToId: pending.replyToId,
    replyToPreview: pending.replyToPreview,
    editCount: 0,
    editedAt: 0,
    createdAt: ack.createdAt,
  };
}
