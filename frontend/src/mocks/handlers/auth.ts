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
  /**
   * 已吊销的 refreshToken 黑名单（契约 §1.3：refresh 每次轮换，旧 token 立即失效；
   * 登出时有效 token 入黑名单）。平台级内存态，随 createMockPlatform 生命周期。
   */
  const revokedRefreshTokens = new Set<string>();

  const revokeRefreshToken = (token: string | undefined | null): void => {
    if (!token) {
      return;
    }
    const payload = parseToken(token);
    if (payload && payload.kind === 'refresh') {
      revokedRefreshTokens.add(token);
    }
  };

  const register: MockHandler = (request) => {
    const body = asRecord(request.body);
    const username = asString(body.username).trim();
    const password = asString(body.password);

    // 契约 §2：username 3~64 字符（后端为 ^[A-Za-z0-9_]+$，mock 保持宽松以兼容中文演示账号）。
    if (username.length < 3 || username.length > 64) {
      return err(400, '用户名需 3~64 字符');
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

  const logout: MockHandler = (request) => {
    // 恒成功；body 中的 refreshToken 入黑名单（契约 §2，无效 token 静默忽略）。
    revokeRefreshToken(asString(asRecord(request.body).refreshToken));
    return ok(null);
  };

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
    const refreshToken = asString(body.refreshToken);
    const payload = parseToken(refreshToken);
    if (!payload || payload.kind !== 'refresh') {
      return err(10005, 'Token 无效或已过期');
    }
    if (revokedRefreshTokens.has(refreshToken)) {
      // 契约：旧 refreshToken 一次性吊销（轮换后立即失效）。
      return err(10005, 'Token 无效或已过期');
    }
    if (payload.expireAt <= ctx.now()) {
      return err(10005, 'Token 无效或已过期');
    }
    // 契约：返回 4 字段 + 新 refreshToken；旧 token 吊销，模拟后端轮换。
    revokedRefreshTokens.add(refreshToken);
    const tokens = issueTokens(payload.userId, ctx.now());
    return ok({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      accessExpire: tokens.accessExpire,
      refreshExpire: tokens.refreshExpire,
    });
  };

  return [
    ['POST /auth/register', register, { isPublic: true }],
    ['POST /auth/login', login, { isPublic: true }],
    ['POST /auth/refresh', refresh, { isPublic: true }],
    ['POST /auth/logout', logout],
    ['GET /auth/validate', validate],
  ];
}
