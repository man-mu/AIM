import { err, ok } from '@/lib/result';
import type { MockHandler } from '../engine/types';
import { asId, asIdArray, asNumber, asRecord, asString, pageParams, type HandlerContext } from './context';

/** User 域 handlers（api-v1.md §3）。 */
export function createUserRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const me: MockHandler = (request) => {
    const user = ctx.db.users.require(request.userId as string);
    return ok(ctx.db.users.toUserInfo(user));
  };

  const updateMe: MockHandler = (request) => {
    const body = asRecord(request.body);
    const patch: Record<string, unknown> = {};
    if (body.avatar !== undefined) patch.avatar = asString(body.avatar);
    if (body.gender !== undefined) patch.gender = asNumber(body.gender) as 0 | 1 | 2;
    if (body.bio !== undefined) patch.bio = asString(body.bio);
    if (body.birthday !== undefined) patch.birthday = asNumber(body.birthday);
    if (body.phone !== undefined) patch.phone = asString(body.phone);
    if (body.email !== undefined) patch.email = asString(body.email);

    const user = ctx.db.users.updateProfile(request.userId as string, patch, ctx.now());
    return ok(ctx.db.users.toUserInfo(user));
  };

  const updatePassword: MockHandler = (request) => {
    const body = asRecord(request.body);
    const oldPassword = asString(body.oldPassword);
    const newPassword = asString(body.newPassword);
    if (newPassword.length < 6 || newPassword.length > 32) {
      return err(400, '新密码需 6~32 字符');
    }
    ctx.db.users.updatePassword(request.userId as string, oldPassword, newPassword, ctx.now());
    return ok(null);
  };

  const getById: MockHandler = (request) => {
    const user = ctx.db.users.require(asId(request.params.userId));
    return ok(ctx.db.users.toUserInfo(user));
  };

  const batch: MockHandler = (request) => {
    // 请求体直接是 long[] 数组（非对象包裹）。
    const ids = asIdArray(request.body);
    const users = ctx.db.users.listByIds(ids).map(ctx.db.users.toUserInfo);
    return ok({ users });
  };

  const search: MockHandler = (request) => {
    const keyword = asString(request.query.keyword).trim();
    const { pageNum, pageSize } = pageParams(request.query);
    if (!keyword) {
      return ok({ users: [], total: 0 });
    }
    const result = ctx.db.users.search(keyword, pageNum, pageSize);
    return ok({ users: result.users.map(ctx.db.users.toUserInfo), total: result.total });
  };

  return [
    ['GET /users/me', me],
    ['PUT /users/me', updateMe],
    ['PUT /users/me/password', updatePassword],
    ['POST /users/batch', batch],
    ['POST /users/search', search],
    ['GET /users/:userId', getById],
  ];
}
