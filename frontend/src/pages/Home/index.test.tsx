import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { UserInfo } from '@/types/User/User';

interface HomeMocks {
  localLogoutMutate: ReturnType<typeof vi.fn>;
  cachedUser: UserInfo | null;
  profileData: UserInfo | undefined;
  isUserLoading: boolean;
  isLoggingOut: boolean;
}

const homeMocks = vi.hoisted<HomeMocks>(() => ({
  localLogoutMutate: vi.fn(),
  cachedUser: {
    id: '1234567890123456789',
    username: 'cached-user',
    phone: '138****8000',
    email: 'cached@example.com',
    avatar: '',
    gender: 0,
    bio: '',
    birthday: 0,
    createdAt: 0,
    updatedAt: 0,
    balance: 0,
  },
  profileData: undefined,
  isUserLoading: false,
  isLoggingOut: false,
}));

vi.mock('@/hooks/useUser', () => ({
  useUser: () => ({ data: homeMocks.profileData, isLoading: homeMocks.isUserLoading }),
}));

vi.mock('@/hooks/useAuth', () => ({
  useLocalLogout: () => ({ mutate: homeMocks.localLogoutMutate, isPending: homeMocks.isLoggingOut }),
}));

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: (selector: (state: { user: UserInfo | null }) => unknown) =>
    selector({ user: homeMocks.cachedUser }),
}));

import Home from './index';

const cachedUser: UserInfo = {
  id: '1234567890123456789',
  username: 'cached-user',
  phone: '138****8000',
  email: 'cached@example.com',
  avatar: '',
  gender: 0,
  bio: '',
  birthday: 0,
  createdAt: 0,
  updatedAt: 0,
  balance: 0,
};

describe('Home', () => {
  beforeEach(() => {
    homeMocks.localLogoutMutate.mockReset();
    homeMocks.cachedUser = cachedUser;
    homeMocks.profileData = undefined;
    homeMocks.isUserLoading = false;
    homeMocks.isLoggingOut = false;
  });

  it('uses the cached user and delegates logout to the local action', () => {
    render(<Home />);

    expect(screen.getByText('cached-user')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' }));

    expect(homeMocks.localLogoutMutate).toHaveBeenCalledTimes(1);
  });

  it('starts local logout only once before mutation state updates', () => {
    render(<Home />);

    const logoutButton = screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' });
    fireEvent.click(logoutButton);
    fireEvent.click(logoutButton);

    expect(homeMocks.localLogoutMutate).toHaveBeenCalledTimes(1);
  });

  it('shows an account skeleton when profile data loads without a cached user', () => {
    homeMocks.cachedUser = null;
    homeMocks.isUserLoading = true;

    render(<Home />);

    expect(screen.getByTestId('account-skeleton')).toBeInTheDocument();
  });

  it('disables logout when the local mutation is pending', () => {
    homeMocks.isLoggingOut = true;

    render(<Home />);

    expect(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' })).toBeDisabled();
  });
});
