import { ok } from '@/lib/result';
import type { MessageContent, MsgType, TextContent } from '@/types/Message/Message';
import type { MockHandler } from '../engine/types';
import { asBoolean, asId, asNumber, asRecord, asString, pageParams, paginate, type HandlerContext } from './context';

/** Message 域 handlers（api-v1.md §6，规划态接口的 mock 实现）。 */
export function createMessageRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const send: MockHandler = (request) => {
    const body = asRecord(request.body);
    const conversationId = asId(body.conversationId);
    const clientMsgId = asString(body.clientMsgId);
    const now = ctx.now();

    const row = ctx.db.messages.append(
      {
        conversationId,
        fromUserId: request.userId as string,
        msgType: (asNumber(body.msgType, 1) || 1) as MsgType,
        content: (body.content ?? { text: '' }) as MessageContent,
        clientMsgId,
        replyToId: asId(body.replyToId) || '0',
      },
      now,
    );

    // 发送者自己视为已读到该条。
    ctx.db.convs.markRead(conversationId, request.userId as string, row.seq);
    ctx.afterUserMessage?.(row);

    return ok({ messageId: row.id, seq: row.seq, createdAt: row.createdAt });
  };

  const recall: MockHandler = (request) => {
    const messageId = asId(request.params.messageId);
    const row = ctx.db.messages.recall(messageId, request.userId as string, ctx.now());
    ctx.events.push({
      event: 'message.recalled',
      data: { messageId: row.id, convId: row.conversationId, userId: request.userId as string },
    });
    return ok(null);
  };

  const edit: MockHandler = (request) => {
    const messageId = asId(request.params.messageId);
    const newContent = (asRecord(request.body).newContent ?? {}) as MessageContent;
    const row = ctx.db.messages.edit(messageId, request.userId as string, newContent, ctx.now());
    ctx.events.push({
      event: 'message.edited',
      data: { messageId: row.id, convId: row.conversationId, userId: request.userId as string, newContent },
    });
    return ok(null);
  };

  const remove: MockHandler = (request) => {
    const messageId = asId(request.params.messageId);
    const deleteForAll = asBoolean(asRecord(request.body).deleteForAll) ?? false;
    ctx.db.messages.delete(messageId, request.userId as string, deleteForAll);
    return ok(null);
  };

  const list: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const cursor = asNumber(request.query.cursor, 0);
    const limit = Math.min(50, Math.max(1, asNumber(request.query.limit, 20)));
    const result = ctx.db.messages.list(conversationId, request.userId as string, cursor, limit);
    return ok({
      list: result.list.map(ctx.db.messages.toDTO),
      nextCursor: result.nextCursor,
      hasMore: result.hasMore,
      total: result.total,
    });
  };

  const sync: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const fromSeq = asNumber(request.query.fromSeq, 0);
    const limit = Math.min(200, Math.max(1, asNumber(request.query.limit, 50)));
    const result = ctx.db.messages.sync(conversationId, request.userId as string, fromSeq, limit);
    return ok({ list: result.list.map(ctx.db.messages.toDTO), hasMore: result.hasMore, maxSeq: result.maxSeq });
  };

  const search: MockHandler = (request) => {
    const keyword = asString(request.query.keyword).trim().toLowerCase();
    const conversationId = asId(request.query.conversationId);
    const { pageNum, pageSize } = pageParams(request.query);
    if (!keyword) {
      return ok({ list: [], total: 0, pageNum, pageSize });
    }

    const userId = request.userId as string;
    const scopeIds = conversationId && conversationId !== '0'
      ? [conversationId]
      : ctx.db.convs.listFor(userId).map((row) => row.id);

    const matched = [];
    for (const convId of scopeIds) {
      // 直接扫全量（mock 规模可接受）；真实实现走服务端索引。
      let cursor = 0;
      for (;;) {
        const page = ctx.db.messages.list(convId, userId, cursor, 50);
        for (const row of page.list) {
          if (row.msgType === 1 && row.status === 1) {
            const text = (row.content as TextContent).text ?? '';
            if (text.toLowerCase().includes(keyword)) {
              matched.push(ctx.db.messages.toDTO(row));
            }
          }
        }
        if (!page.hasMore || !page.nextCursor) {
          break;
        }
        cursor = Number(page.nextCursor);
      }
    }

    matched.sort((a, b) => b.createdAt - a.createdAt);
    const { slice, total } = paginate(matched, pageNum, pageSize);
    return ok({ list: slice, total, pageNum, pageSize });
  };

  return [
    ['POST /messages/send', send],
    ['GET /messages/search', search],
    ['POST /messages/:messageId/recall', recall],
    ['POST /messages/:messageId/reply', send],
    ['PUT /messages/:messageId', edit],
    ['DELETE /messages/:messageId', remove],
    ['GET /messages/:conversationId', list],
    ['GET /messages/:conversationId/sync', sync],
  ];
}
