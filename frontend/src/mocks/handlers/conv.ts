import { ok } from '@/lib/result';
import { toMessageNewData } from '@/realtime/protocol';
import type { SystemContent } from '@/types/Message/Message';
import type { MockHandler } from '../engine/types';
import { asBoolean, asId, asIdArray, asNumber, asRecord, asString, pageParams, paginate, type HandlerContext } from './context';

/**
 * Conversation 域 handlers（api-v1-implemented.md §4）。
 * 成员变更 / 公告等操作会追加系统消息（msgType=7）并推送实时事件，
 * 让前端在 mock 环境下也能体验完整的数据流闭环。
 */
export function createConvRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const usernameOf = (userId: string): string => ctx.db.users.get(userId)?.username ?? '未知用户';

  /** 系统消息 + message.new 事件（面向当前用户）。 */
  const appendSystemMessage = (conversationId: string, actorId: string, action: string, detail: string, forUserId: string): void => {
    const now = ctx.now();
    const content: SystemContent = { action, detail, actorId, actorType: 'user' };
    const row = ctx.db.messages.append(
      {
        conversationId,
        fromUserId: actorId,
        msgType: 7,
        content,
        clientMsgId: `sys-${conversationId}-${now}-${Math.floor(Math.random() * 1e6)}`,
        skipGuards: true,
      },
      now,
    );
    const member = ctx.db.convs.getMember(conversationId, forUserId);
    const conversation = ctx.db.convs.get(conversationId);
    const unread = member && conversation ? Math.max(0, conversation.maxSeq - member.lastReadSeq) : 0;
    ctx.events.push({
      event: 'message.new',
      data: toMessageNewData(ctx.db.messages.toDTO(row), { id: actorId, username: usernameOf(actorId), avatar: ctx.db.users.get(actorId)?.avatar ?? '' }, unread),
    });
  };

  const create: MockHandler = (request) => {
    const body = asRecord(request.body);
    const type = asNumber(body.type);
    const now = ctx.now();
    const creatorId = request.userId as string;

    if (type === 1) {
      const peerUserId = asId(body.peerUserId);
      const row = ctx.db.convs.createDirect(creatorId, peerUserId, now);
      return ok({ conversationId: row.id, conversation: ctx.db.convs.toDTO(row, creatorId) });
    }

    const row = ctx.db.convs.createGroup(
      creatorId,
      { name: asString(body.name, '未命名群聊'), avatar: asString(body.avatar), memberIds: asIdArray(body.memberIds) },
      now,
    );
    appendSystemMessage(row.id, creatorId, 'group.created', `${usernameOf(creatorId)} 创建了群聊`, creatorId);
    return ok({ conversationId: row.id, conversation: ctx.db.convs.toDTO(row, creatorId) });
  };

  const detail: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const row = ctx.db.convs.require(conversationId);
    ctx.db.convs.requireMember(conversationId, request.userId as string);
    return ok(ctx.db.convs.toDTO(row, request.userId as string));
  };

  const list: MockHandler = (request) => {
    const { pageNum, pageSize } = pageParams(request.query);
    const rows = ctx.db.convs
      .listFor(request.userId as string)
      .sort((a, b) => b.lastMessageAt - a.lastMessageAt);
    const { slice, total } = paginate(rows, pageNum, pageSize);
    return ok({
      conversations: slice.map((row) => ctx.db.convs.toDTO(row, request.userId as string)),
      total,
    });
  };

  const members: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    ctx.db.convs.require(conversationId);
    ctx.db.convs.requireMember(conversationId, request.userId as string);
    const { pageNum, pageSize } = pageParams(request.query, 50);
    const rows = ctx.db.convs.listMembers(conversationId);
    const { slice, total } = paginate(rows, pageNum, pageSize);
    return ok({ members: slice.map((member) => ctx.db.convs.toMemberDTO(member)), total });
  };

  const invite: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const userIds = asIdArray(asRecord(request.body).userIds);
    const operatorId = request.userId as string;
    const result = ctx.db.convs.addMembers(conversationId, operatorId, userIds, ctx.now());

    if (result.addedUserIds.length > 0) {
      const names = result.addedUserIds.map(usernameOf).join('、');
      appendSystemMessage(conversationId, operatorId, 'member.joined', `${usernameOf(operatorId)} 邀请 ${names} 加入群聊`, operatorId);
      ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    }
    return ok(result);
  };

  const kick: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const userIds = asIdArray(asRecord(request.body).userIds);
    const operatorId = request.userId as string;
    const names = userIds.map(usernameOf).join('、');
    ctx.db.convs.kickMembers(conversationId, operatorId, userIds, ctx.now());
    appendSystemMessage(conversationId, operatorId, 'member.removed', `${usernameOf(operatorId)} 将 ${names} 移出群聊`, operatorId);
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const mute: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const targetId = asId(request.params.userId);
    const durationSeconds = asNumber(asRecord(request.body).durationSeconds);
    ctx.db.convs.muteMember(conversationId, request.userId as string, targetId, durationSeconds, ctx.now());
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const unmute: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const targetId = asId(request.params.userId);
    ctx.db.convs.unmuteMember(conversationId, request.userId as string, targetId, ctx.now());
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const transfer: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const newOwnerId = asId(asRecord(request.body).newOwnerId);
    const operatorId = request.userId as string;
    ctx.db.convs.transferOwner(conversationId, operatorId, newOwnerId, ctx.now());
    appendSystemMessage(conversationId, operatorId, 'owner.transferred', `${usernameOf(operatorId)} 将群主转让给 ${usernameOf(newOwnerId)}`, operatorId);
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const setAnnouncement: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const content = asString(asRecord(request.body).content);
    const operatorId = request.userId as string;
    ctx.db.convs.setAnnouncement(conversationId, operatorId, content, ctx.now());
    if (content) {
      appendSystemMessage(conversationId, operatorId, 'announcement.updated', `${usernameOf(operatorId)} 更新了群公告`, operatorId);
    }
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const deleteAnnouncement: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    ctx.db.convs.setAnnouncement(conversationId, request.userId as string, '', ctx.now());
    ctx.events.push({ event: 'conversation.updated', data: { convId: conversationId } });
    return ok(null);
  };

  const getSettings: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    return ok(ctx.db.convs.getSettings(conversationId, request.userId as string));
  };

  const updateSettings: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const body = asRecord(request.body);
    ctx.db.convs.updateSettings(conversationId, request.userId as string, {
      isMuted: asBoolean(body.isMuted),
      isPinned: asBoolean(body.isPinned),
      nickname: body.nickname !== undefined ? asString(body.nickname) : undefined,
    });
    return ok(null);
  };

  const markRead: MockHandler = (request) => {
    const conversationId = asId(request.params.conversationId);
    const seq = asNumber(asRecord(request.body).seq);
    ctx.db.convs.markRead(conversationId, request.userId as string, seq);
    return ok(null);
  };

  return [
    ['POST /convs', create],
    ['GET /convs', list],
    ['GET /convs/:conversationId', detail],
    ['GET /convs/:conversationId/members', members],
    ['POST /convs/:conversationId/members/invite', invite],
    ['POST /convs/:conversationId/members/kick', kick],
    ['PUT /convs/:conversationId/members/:userId/mute', mute],
    ['DELETE /convs/:conversationId/members/:userId/mute', unmute],
    ['POST /convs/:conversationId/transfer', transfer],
    ['PUT /convs/:conversationId/announcement', setAnnouncement],
    ['DELETE /convs/:conversationId/announcement', deleteAnnouncement],
    ['GET /convs/:conversationId/settings', getSettings],
    ['PUT /convs/:conversationId/settings', updateSettings],
    ['PUT /convs/:conversationId/read', markRead],
  ];
}
