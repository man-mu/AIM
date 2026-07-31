import { err, ok } from '@/lib/result';
import { issueTokens, parseToken } from '../engine/tokens';
import type { MockHandler } from '../engine/types';
import { asRecord, asString, type HandlerContext } from './context';

/**
 * Auth 域 handlers（api-v1-implemented.md §2）。
 * 注册 / 登录成功即为用户搭建初始世界（幂等）。
 */
export function createAuthRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const register: MockHandler = (request) => {
    const body = asRecord(request.body);
    const username = asString(body.username).trim();
    const password = asString(body.password);

    if (username.length < 3 || username.length > 32) {
      return err(400, '用户名需 3~32 字符');
    }
    if (password.length < 6 || password.length > 32) {
      return err(400, '密码需 6~32 字符');
    }

    const now = ctx.now();
    const user = ctx.db.users.create(
      {
        username,
        password,
        phone: asString(body.phone) || undefined,
        email: asString(body.email) || undefined,
      },
      now,
    );
    ctx.db.bootstrapWorldFor(user.id, now);

    return ok({
      userId: user.id,
      tokens: issueTokens(user.id, now),
      user: ctx.db.users.toUserInfo(user),
    });
  };

  const login: MockHandler = (request) => {
    const body = asRecord(request.body);
    const account = asString(body.account).trim();
    const password = asString(body.password);
    if (!account || !password) {
      return err(400, '账号和密码不能为空');
    }

    const now = ctx.now();
    const user = ctx.db.users.verifyPassword(account, password);
    ctx.db.bootstrapWorldFor(user.id, now);

    return ok({
      userId: user.id,
      tokens: issueTokens(user.id, now),
      user: ctx.db.users.toUserInfo(user),
    });
  };

  const logout: MockHandler = () => ok(null);

  const validate: MockHandler = (request) => {
    // 走到这里说明引擎鉴权已通过。
    return ok({
      valid: true,
      userId: request.userId,
      expiresAt: ctx.now() + 2 * 60 * 60 * 1000,
    });
  };

  const refresh: MockHandler = (request) => {
    const body = asRecord(request.body);
    const payload = parseToken(asString(body.refreshToken));
    if (!payload || payload.kind !== 'refresh') {
      return err(10005, 'Token 无效或已过期');
    }
    if (payload.expireAt <= ctx.now()) {
      return err(10006, 'Token 已过期');
    }
    const tokens = issueTokens(payload.userId, ctx.now());
    return ok({ accessToken: tokens.accessToken, accessExpire: tokens.accessExpire });
  };

  return [
    ['POST /auth/register', register, { isPublic: true }],
    ['POST /auth/login', login, { isPublic: true }],
    ['POST /auth/refresh', refresh, { isPublic: true }],
    ['POST /auth/logout', logout],
    ['GET /auth/validate', validate],
  ];
}
