import { ok } from '@/lib/result';
import type { FriendDTO, FriendGroupDTO, FriendRequestDTO } from '@/types/Friend/Friend';
import type { MockHandler } from '../engine/types';
import { asId, asRecord, asString, pageParams, paginate, type HandlerContext } from './context';

/** Friend 域 handlers（api-v1.md §4，规划态接口的 mock 实现）。 */
export function createFriendRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const toRequestDTO = (row: {
    id: string;
    fromUserId: string;
    toUserId: string;
    message: string;
    status: 1 | 2 | 3 | 4;
    createdAt: number;
    updatedAt: number;
  }): FriendRequestDTO => {
    const from = ctx.db.users.get(row.fromUserId);
    const to = ctx.db.users.get(row.toUserId);
    return {
      requestId: row.id,
      fromUserId: row.fromUserId,
      fromUsername: from?.username ?? '未知用户',
      fromAvatar: from?.avatar ?? '',
      toUserId: row.toUserId,
      toUsername: to?.username ?? '未知用户',
      toAvatar: to?.avatar ?? '',
      message: row.message,
      status: row.status,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    };
  };

  const sendRequest: MockHandler = (request) => {
    const body = asRecord(request.body);
    const toUserId = asId(body.toUserId);
    const row = ctx.db.social.createRequest(request.userId as string, toUserId, asString(body.message), ctx.now());
    return ok({ requestId: row.id });
  };

  const acceptRequest: MockHandler = (request) => {
    const row = ctx.db.social.acceptRequest(asId(request.params.requestId), request.userId as string, ctx.now());
    return ok(toRequestDTO(row));
  };

  const rejectRequest: MockHandler = (request) => {
    const row = ctx.db.social.rejectRequest(asId(request.params.requestId), request.userId as string, ctx.now());
    return ok(toRequestDTO(row));
  };

  const cancelRequest: MockHandler = (request) => {
    ctx.db.social.cancelRequest(asId(request.params.requestId), request.userId as string, ctx.now());
    return ok(null);
  };

  const pendingRequests: MockHandler = (request) => {
    const rows = ctx.db.social.listRequests(request.userId as string, 'incoming').filter((row) => row.status === 1);
    const { pageNum, pageSize } = pageParams(request.query);
    const { slice, total } = paginate(rows, pageNum, pageSize);
    return ok({ list: slice.map(toRequestDTO), total, pageNum, pageSize });
  };

  const sentRequests: MockHandler = (request) => {
    const rows = ctx.db.social.listRequests(request.userId as string, 'outgoing');
    const { pageNum, pageSize } = pageParams(request.query);
    const { slice, total } = paginate(rows, pageNum, pageSize);
    return ok({ list: slice.map(toRequestDTO), total, pageNum, pageSize });
  };

  const listFriends: MockHandler = (request) => {
    const ownerId = request.userId as string;
    const groupId = asId(request.query.groupId) || '0';
    const rows = ctx.db.social.listFriends(ownerId, groupId);
    const { pageNum, pageSize } = pageParams(request.query, 50);
    const groups = new Map(ctx.db.social.listGroups(ownerId).map((group) => [group.id, group.name]));

    const list: FriendDTO[] = rows.map((row) => {
      const user = ctx.db.users.get(row.friendId);
      return {
        userId: row.friendId,
        username: user?.username ?? '未知用户',
        avatar: user?.avatar ?? '',
        remark: row.remark,
        groupId: row.groupId,
        groupName: row.groupId === '0' ? '默认分组' : (groups.get(row.groupId) ?? '默认分组'),
        status: ctx.db.social.presenceOf(row.friendId),
        createdAt: row.createdAt,
      };
    });
    const { slice, total } = paginate(list, pageNum, pageSize);
    return ok({ list: slice, total, pageNum, pageSize });
  };

  const removeFriend: MockHandler = (request) => {
    ctx.db.social.removeFriendPair(request.userId as string, asId(request.params.friendId));
    return ok(null);
  };

  const setRemark: MockHandler = (request) => {
    const body = asRecord(request.body);
    ctx.db.social.setFriendRemark(request.userId as string, asId(request.params.friendId), asString(body.remark));
    return ok(null);
  };

  const moveGroup: MockHandler = (request) => {
    const body = asRecord(request.body);
    ctx.db.social.moveFriendToGroup(request.userId as string, asId(request.params.friendId), asId(body.groupId) || '0');
    return ok(null);
  };

  const createGroup: MockHandler = (request) => {
    const name = asString(asRecord(request.body).name).trim();
    const row = ctx.db.social.createGroup(request.userId as string, name || '新建分组', ctx.now());
    return ok({ groupId: row.id, name: row.name });
  };

  const renameGroup: MockHandler = (request) => {
    const name = asString(asRecord(request.body).name).trim();
    const row = ctx.db.social.renameGroup(request.userId as string, asId(request.params.groupId), name);
    return ok({ groupId: row.id, name: row.name });
  };

  const deleteGroup: MockHandler = (request) => {
    ctx.db.social.deleteGroup(request.userId as string, asId(request.params.groupId));
    return ok(null);
  };

  const listGroups: MockHandler = (request) => {
    const ownerId = request.userId as string;
    const friends = ctx.db.social.listFriends(ownerId);
    const countBy = new Map<string, number>();
    for (const friend of friends) {
      countBy.set(friend.groupId, (countBy.get(friend.groupId) ?? 0) + 1);
    }

    const groups: FriendGroupDTO[] = [
      { groupId: '0', name: '默认分组', friendCount: countBy.get('0') ?? 0, createdAt: 0 },
      ...ctx.db.social.listGroups(ownerId).map((group) => ({
        groupId: group.id,
        name: group.name,
        friendCount: countBy.get(group.id) ?? 0,
        createdAt: group.createdAt,
      })),
    ];
    return ok({ list: groups, total: groups.length });
  };

  const block: MockHandler = (request) => {
    ctx.db.social.block(request.userId as string, asId(request.params.userId), ctx.now());
    return ok(null);
  };

  const unblock: MockHandler = (request) => {
    ctx.db.social.unblock(request.userId as string, asId(request.params.userId));
    return ok(null);
  };

  const blacklist: MockHandler = (request) => {
    const rows = ctx.db.social.listBlacklist(request.userId as string);
    const { pageNum, pageSize } = pageParams(request.query);
    const list = rows.map((row) => {
      const user = ctx.db.users.get(row.blockedId);
      return {
        userId: row.blockedId,
        username: user?.username ?? '未知用户',
        avatar: user?.avatar ?? '',
        createdAt: row.createdAt,
      };
    });
    const { slice, total } = paginate(list, pageNum, pageSize);
    return ok({ list: slice, total, pageNum, pageSize });
  };

  return [
    ['POST /friends/requests', sendRequest],
    ['GET /friends/requests/pending', pendingRequests],
    ['GET /friends/requests/sent', sentRequests],
    ['POST /friends/requests/:requestId/accept', acceptRequest],
    ['POST /friends/requests/:requestId/reject', rejectRequest],
    ['DELETE /friends/requests/:requestId', cancelRequest],
    ['GET /friends', listFriends],
    ['GET /friends/groups', listGroups],
    ['POST /friends/groups', createGroup],
    ['PUT /friends/groups/:groupId', renameGroup],
    ['DELETE /friends/groups/:groupId', deleteGroup],
    ['GET /friends/blacklist', blacklist],
    ['POST /friends/blacklist/:userId', block],
    ['DELETE /friends/blacklist/:userId', unblock],
    ['DELETE /friends/:friendId', removeFriend],
    ['PUT /friends/:friendId/remark', setRemark],
    ['PUT /friends/:friendId/group', moveGroup],
  ];
}
