import { fireEvent, render, screen, within } from '@testing-library/react';
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

const workspaceSlots = {
  sidebarContent: <p>{'\u4f1a\u8bdd\u5217\u8868'}</p>,
  chatContent: <h1>{'\u6797\u5ddd'}</h1>,
  detailContent: <p>{'\u4f1a\u8bdd\u8be6\u60c5'}</p>,
};

describe('HomeShell', () => {
  it('renders workspace slots and delegates logout to its callback', () => {
    const onLogout = vi.fn();

    render(
      <HomeShell
        user={user}
        isUserLoading={false}
        isLoggingOut={false}
        isMobileChatOpen={false}
        onLogout={onLogout}
        {...workspaceSlots}
      />,
    );

    const sidebar = within(screen.getByTestId('home-sidebar'));
    const chatPanel = within(screen.getByTestId('home-chat-panel'));
    const detailPanel = within(screen.getByTestId('home-detail-panel'));

    expect(sidebar.getByText('\u4f1a\u8bdd\u5217\u8868')).toBeInTheDocument();
    expect(chatPanel.getByRole('heading', { name: '\u6797\u5ddd' })).toBeInTheDocument();
    expect(detailPanel.getByText('\u4f1a\u8bdd\u8be6\u60c5')).toBeInTheDocument();
    expect(sidebar.queryByText('\u6797\u5ddd')).not.toBeInTheDocument();
    expect(sidebar.queryByText('\u4f1a\u8bdd\u8be6\u60c5')).not.toBeInTheDocument();
    expect(screen.getByText('zhangsan')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' }));

    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('uses a fixed account skeleton while profile data loads and disables a pending logout', () => {
    render(
      <HomeShell
        user={null}
        isUserLoading
        isLoggingOut
        isMobileChatOpen={false}
        onLogout={vi.fn()}
        {...workspaceSlots}
      />,
    );

    expect(screen.getByTestId('account-skeleton')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '\u9000\u51fa\u767b\u5f55' })).toBeDisabled();
  });

  it('switches sidebar and chat visibility classes for the mobile chat state', () => {
    const { rerender } = render(
      <HomeShell
        user={user}
        isUserLoading={false}
        isLoggingOut={false}
        isMobileChatOpen={false}
        onLogout={vi.fn()}
        {...workspaceSlots}
      />,
    );

    expect(screen.getByTestId('home-sidebar')).not.toHaveClass('hidden');
    expect(screen.getByTestId('home-sidebar')).toHaveClass('flex');
    expect(screen.getByTestId('home-chat-panel')).toHaveClass('hidden');
    expect(screen.getByTestId('home-chat-panel')).toHaveClass('sm:flex');
    expect(screen.getByTestId('home-detail-panel')).toHaveClass('lg:block');

    rerender(
      <HomeShell
        user={user}
        isUserLoading={false}
        isLoggingOut={false}
        isMobileChatOpen
        onLogout={vi.fn()}
        {...workspaceSlots}
      />,
    );

    expect(screen.getByTestId('home-sidebar')).toHaveClass('hidden');
    expect(screen.getByTestId('home-sidebar')).toHaveClass('sm:flex');
    expect(screen.getByTestId('home-chat-panel')).not.toHaveClass('hidden');
    expect(screen.getByTestId('home-chat-panel')).toHaveClass('flex');
  });
});
