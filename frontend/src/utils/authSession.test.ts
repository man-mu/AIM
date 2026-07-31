import { beforeEach, describe, expect, it, vi } from 'vitest';
import { establishAuthSession } from './authSession';
import { storage } from './storage';
import type { LoginData } from '@/types/Auth/Auth';

const loginData: LoginData = {
  userId: '1234567890123456789',
  tokens: {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    accessExpire: 1707123400000,
    refreshExpire: 1707728200000,
  },
  user: {
    id: '1234567890123456789',
    username: 'zhangsan',
    phone: '138****8000',
    email: 'zhan****@foo.com',
    avatar: '',
    gender: 0,
    bio: '',
    birthday: 0,
    createdAt: 1707100000000,
    updatedAt: 1707100000000,
    balance: 0,
  },
};

describe('establishAuthSession', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('persists all token metadata and synchronizes the authenticated user', () => {
    const setAuth = vi.fn();

    establishAuthSession(loginData, setAuth);

    expect(storage.getAccessToken()).toBe('access-token');
    expect(storage.getRefreshToken()).toBe('refresh-token');
    expect(storage.getAccessExpire()).toBe(1707123400000);
    expect(storage.getRefreshExpire()).toBe(1707728200000);
    expect(setAuth).toHaveBeenCalledWith(loginData.user);
  });
});
