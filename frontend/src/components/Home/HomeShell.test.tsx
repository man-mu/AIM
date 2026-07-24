import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { UserInfo } from '@/types/User/User';
import HomeShell from './HomeShell';

const user: UserInfo = {
  id: '1234567890123456789',
  username: 'zhangsan',
  phone: '138****8000',
  email: 'zhan****@foo.com',
  avatar: '',
  gender: 0,
  bio: '',
  birthday: 0,
  createdAt: 0,
  updatedAt: 0,
  balance: 0,
};

describe('HomeShell', () => {
  it('renders the message workspace and delegates logout to its callback', () => {
    const onLogout = vi.fn();

    render(<HomeShell user={user} isUserLoading={false} isLoggingOut={false} onLogout={onLogout} />);

    expect(screen.getByRole('heading', { name: '\u6d88\u606f' })).toBeInTheDocument();
    expect(screen.getByText('\u5f00\u59cb\u4e00\u6bb5\u65b0\u5bf9\u8bdd')).toBeInTheDocument();
    expect(screen.getByText('zhangsan')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' }));

    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('uses a fixed account skeleton while profile data loads and disables a pending logout', () => {
    render(<HomeShell user={null} isUserLoading isLoggingOut onLogout={vi.fn()} />);

    expect(screen.getByTestId('account-skeleton')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' })).toBeDisabled();
  });
});
