import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LoginData } from '@/types/Auth/Auth';
import { subscribeToToasts } from '@/components/ui/toast/toastBus';
import { useAuthStore } from '@/stores/useAuthStore';
import { storage } from '@/utils/storage';

const authApiMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('@/apis/auth.ts', () => ({ authApi: authApiMock }));

import { useLocalLogout, useLogin, useLogout, useRegister } from './useAuth';

const authData: LoginData = {
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

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function createWrapper(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return function Wrapper({ children }: PropsWithChildren) {
    return (
      <MemoryRouter initialEntries={[initialEntry]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        <LocationProbe />
      </MemoryRouter>
    );
  };
}

describe('authentication mutations', () => {
  const toasts: string[] = [];
  let unsubscribeToasts: () => void;

  beforeEach(() => {
    localStorage.clear();
    authApiMock.login.mockReset();
    authApiMock.register.mockReset();
    authApiMock.logout.mockReset();
    useAuthStore.setState({ isLogin: false, user: null });
    toasts.length = 0;
    unsubscribeToasts = subscribeToToasts((item) => toasts.push(item.text));
  });

  afterEach(() => {
    unsubscribeToasts();
  });

  it('establishes a session and redirects after login', async () => {
    authApiMock.login.mockResolvedValue(authData);
    const { result } = renderHook(() => useLogin(), { wrapper: createWrapper('/login') });

    await result.current.mutateAsync({
      account: 'zhangsan',
      password: 'Abc@123456',
      deviceId: 'device-uuid',
      platform: 'web',
    });

    await waitFor(() => expect(document.querySelector('[data-testid="location"]')).toHaveTextContent('/home'));
    expect(storage.getAccessToken()).toBe('access-token');
    expect(useAuthStore.getState().user).toEqual(authData.user);
  });

  it('establishes a session and redirects after registration', async () => {
    authApiMock.register.mockResolvedValue(authData);
    const { result } = renderHook(() => useRegister(), { wrapper: createWrapper('/register') });

    await result.current.mutateAsync({
      username: 'zhangsan',
      password: 'Abc@123456',
      deviceId: 'device-uuid',
      platform: 'web',
    });

    await waitFor(() => expect(document.querySelector('[data-testid="location"]')).toHaveTextContent('/home'));
    expect(storage.getAccessToken()).toBe('access-token');
    expect(useAuthStore.getState().user).toEqual(authData.user);
  });

  it('surfaces the business error as a toast when registration fails', async () => {
    authApiMock.register.mockRejectedValue(new Error('用户名已存在'));
    const { result } = renderHook(() => useRegister(), { wrapper: createWrapper('/register') });

    await expect(
      result.current.mutateAsync({
        username: 'zhangsan',
        password: 'Abc@123456',
        deviceId: 'device-uuid',
        platform: 'web',
      }),
    ).rejects.toThrow('用户名已存在');

    expect(toasts).toContain('用户名已存在');
  });

  it('clears local session state and returns to login without calling the API', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const Wrapper = ({ children }: PropsWithChildren) => (
      <MemoryRouter initialEntries={['/home']}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        <LocationProbe />
      </MemoryRouter>
    );

    storage.setAccessToken('access-token');
    storage.setRefreshToken('refresh-token');
    storage.setAccessExpire(Date.now() + 60_000);
    storage.setRefreshExpire(Date.now() + 60_000);
    queryClient.setQueryData(['user'], authData.user);
    useAuthStore.setState({ isLogin: true, user: authData.user });

    const { result } = renderHook(() => useLocalLogout(), { wrapper: Wrapper });
    await result.current.mutateAsync();

    await waitFor(() => expect(document.querySelector('[data-testid="location"]')).toHaveTextContent('/login'));
    expect(storage.getAccessToken()).toBeNull();
    expect(storage.getRefreshToken()).toBeNull();
    expect(queryClient.getQueryData(['user'])).toBeUndefined();
    expect(useAuthStore.getState()).toMatchObject({ isLogin: false, user: null });
    expect(authApiMock.logout).not.toHaveBeenCalled();
    expect(toasts).toContain('已退出登录');
  });

  it('revokes the refresh token on logout and clears the session even if the API fails', async () => {
    storage.setRefreshToken('refresh-token');
    authApiMock.logout.mockRejectedValue(new Error('network down'));

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper('/home') });
    await result.current.mutateAsync();

    await waitFor(() => expect(document.querySelector('[data-testid="location"]')).toHaveTextContent('/login'));
    expect(authApiMock.logout).toHaveBeenCalledWith({ refreshToken: 'refresh-token' });
    expect(storage.getRefreshToken()).toBeNull();
    expect(toasts).toContain('已退出登录');
  });
});
