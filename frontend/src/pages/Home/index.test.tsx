import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/stores/useAuthStore';

/** Home 页 smoke：路由挂载 → 工作台三区骨架渲染。 */
const convApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  getSettings: vi.fn(),
  getMembers: vi.fn(),
  getDetail: vi.fn(),
  markRead: vi.fn(),
  create: vi.fn(),
}));
const messageApiMock = vi.hoisted(() => ({ list: vi.fn(), send: vi.fn() }));

vi.mock('@/apis/conv', () => ({ convApi: convApiMock }));
vi.mock('@/apis/message', () => ({ messageApi: messageApiMock }));

import Home from './index';

describe('Home page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      isLogin: true,
      user: {
        id: '1001',
        username: '我自己',
        phone: '',
        email: '',
        avatar: '',
        gender: 0,
        bio: '',
        birthday: 0,
        createdAt: 0,
        updatedAt: 0,
        balance: '0',
      },
    });
    convApiMock.list.mockResolvedValue({ conversations: [], total: 0 });
  });

  it('renders workspace skeleton with sidebar and empty chat state', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <MemoryRouter initialEntries={['/home']}>
        <QueryClientProvider client={queryClient}>
          <Routes>
            <Route path="home" element={<Home />}>
              <Route index element={null} />
            </Route>
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('conversation-sidebar')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('选择一个会话开始聊天')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText('暂无会话')).toBeInTheDocument());
  });
});
