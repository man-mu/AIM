/**
 * Mock JWT：`mock.<a|r>.<userId>.<expireMs>` 四段字符串。
 * 不做签名，只承载「谁 + 何时过期」，足以驱动前端完整的鉴权/刷新流程。
 */
export interface MockTokenPayload {
  kind: 'access' | 'refresh';
  userId: string;
  expireAt: number;
}

export const ACCESS_TTL_MS = 2 * 60 * 60 * 1000;
export const REFRESH_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export interface IssuedTokens {
  accessToken: string;
  refreshToken: string;
  accessExpire: number;
  refreshExpire: number;
}

/** 发号序列：保证同一毫秒内重复签发的 token 也互不相同（模拟 jti）。 */
let tokenSerial = 0;

export function issueTokens(userId: string, now: number): IssuedTokens {
  const accessExpire = now + ACCESS_TTL_MS;
  const refreshExpire = now + REFRESH_TTL_MS;
  tokenSerial += 1;
  return {
    accessToken: `mock.a.${userId}.${accessExpire}.${tokenSerial}`,
    refreshToken: `mock.r.${userId}.${refreshExpire}.${tokenSerial}`,
    accessExpire,
    refreshExpire,
  };
}

export function parseToken(token: string | undefined | null): MockTokenPayload | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if ((parts.length !== 4 && parts.length !== 5) || parts[0] !== 'mock') {
    return null;
  }
  const [, kindFlag, userId, expireRaw] = parts as [string, string, string, string, ...string[]];
  const expireAt = Number(expireRaw);
  if (!userId || !Number.isFinite(expireAt)) {
    return null;
  }
  if (kindFlag !== 'a' && kindFlag !== 'r') {
    return null;
  }
  return { kind: kindFlag === 'a' ? 'access' : 'refresh', userId, expireAt };
}

export function parseBearer(header: string | undefined | null): MockTokenPayload | null {
  if (!header || !header.startsWith('Bearer ')) {
    return null;
  }
  return parseToken(header.slice('Bearer '.length));
}
