import type { Int64, Tokens, UserInfo } from '../User/User.ts';

export type Platform = 'ios' | 'android' | 'web';

// POST /auth/login
export interface LoginParams {
  /** 用户名 / 手机号 / 邮箱。 */
  account: string;
  password: string;
  deviceId: string;
  platform: Platform;
}
export type LoginData = RegisterData;

// POST /auth/logout（accessToken 从 Header 提取，body 只传 refreshToken）
export interface LogoutParams {
  refreshToken: string;
}

// POST /auth/register
export interface RegisterParams {
  /** 3~32 字符。 */
  username: string;
  /** 6~32 字符。 */
  password: string;
  phone?: string;
  email?: string;
  deviceId: string;
  platform: Platform;
}
export interface RegisterData {
  userId: Int64;
  tokens: Tokens;
  user: UserInfo;
}

// GET /auth/validate
export interface ValidateData {
  valid: boolean;
  userId: Int64;
  expiresAt: number;
}

// POST /auth/refresh（仅返回新的 accessToken，refreshToken 不变）
export interface RefreshParams {
  refreshToken: string;
}
export interface RefreshData {
  accessToken: string;
  accessExpire: number;
}
