import type { LoginData } from '@/types/Auth/Auth';
import type { UserInfo } from '@/types/User/User';
import { storage } from './storage';

type SetAuth = (user: UserInfo) => void;

export function establishAuthSession(data: LoginData, setAuth: SetAuth): void {
  const { tokens, user } = data;

  storage.setAccessToken(tokens.accessToken);
  storage.setRefreshToken(tokens.refreshToken);
  storage.setAccessExpire(tokens.accessExpire);
  storage.setRefreshExpire(tokens.refreshExpire);
  setAuth(user);
}

export function hasValidAuthSession(): boolean {
  const accessToken = storage.getAccessToken();
  const accessExpire = storage.getAccessExpire();

  return Boolean(accessToken && accessExpire > Date.now());
}
