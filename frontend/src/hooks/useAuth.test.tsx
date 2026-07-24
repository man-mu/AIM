import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { PropsWithChildren } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LoginData } from '@/types/Auth/Auth';
import { useAuthStore } from '@/stores/useAuthStore';
import { storage } from '@/utils/storage';

const authApiMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('@/apis/auth.ts', () => ({ authApi: authApiMock }));

import { useLogin, useRegister } from './useAuth';

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
  beforeEach(() => {
    localStorage.clear();
    authApiMock.login.mockReset();
    authApiMock.register.mockReset();
    useAuthStore.setState({ isLogin: false, user: null });
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

  it('shows the business error returned by registration', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
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

    expect(alertSpy).toHaveBeenCalledWith('用户名已存在');
  });
});
