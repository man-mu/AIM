import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const homeMocks = vi.hoisted(() => ({
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
}));

vi.mock('@/hooks/useUser', () => ({
  useUser: () => ({ data: undefined, isLoading: false }),
}));

vi.mock('@/hooks/useAuth', () => ({
  useLocalLogout: () => ({ mutate: homeMocks.localLogoutMutate, isPending: false }),
}));

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: (selector: (state: { user: typeof homeMocks.cachedUser }) => unknown) =>
    selector({ user: homeMocks.cachedUser }),
}));

import Home from './index';

describe('Home', () => {
  it('uses the cached user and delegates logout to the local action', () => {
    render(<Home />);

    expect(screen.getByText('cached-user')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' }));

    expect(homeMocks.localLogoutMutate).toHaveBeenCalledTimes(1);
  });
});
