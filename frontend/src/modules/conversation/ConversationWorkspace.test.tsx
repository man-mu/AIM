import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConversationDTO } from '@/types/Conversation/Conversation';
import type { ListMessagesData } from '@/types/Message/Message';
import { useAuthStore } from '@/stores/useAuthStore';
import { useWorkspaceStore } from '@/stores/useWorkspaceStore';

/**
 * 工作台集成测试：在 apis 层打桩（与真实/mock 后端同一契约），
 * 验证「列表加载 → 选中会话 → 历史渲染 → 乐观发送 → 自动已读」的数据流闭环。
 */
const convApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  getSettings: vi.fn(),
  getMembers: vi.fn(),
  getDetail: vi.fn(),
  markRead: vi.fn(),
  create: vi.fn(),
  invite: vi.fn(),
  kick: vi.fn(),
  muteMember: vi.fn(),
  unmuteMember: vi.fn(),
  transferOwner: vi.fn(),
  setAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
  updateSettings: vi.fn(),
}));
const messageApiMock = vi.hoisted(() => ({
  list: vi.fn(),
  send: vi.fn(),
  sync: vi.fn(),
  recall: vi.fn(),
  edit: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('@/apis/conv', () => ({ convApi: convApiMock }));
vi.mock('@/apis/message', () => ({ messageApi: messageApiMock }));

import { ConversationWorkspace } from './ConversationWorkspace';

const ME = {
  id: '1001',
  username: '我自己',
  phone: '',
  email: '',
  avatar: '',
  gender: 0 as const,
  bio: '',
  birthday: 0,
  createdAt: 0,
  updatedAt: 0,
  balance: '0',
};

function conversationDto(overrides: Partial<ConversationDTO>): ConversationDTO {
  return {
    id: 'c1',
    type: 2,
    name: '评审组',
    avatar: '',
    ownerId: '1001',
    memberCount: 3,
    maxSeq: 2,
    lastMessageId: 'm2',
    lastMessagePreview: '第二条',
    announcement: '',
    isMutedAll: false,
    createdAt: 1,
    updatedAt: 2000,
    unreadCount: 1,
    ...overrides,
  };
}

function messagesPage(): ListMessagesData {
  return {
    list: [
      {
        messageId: 'm2',
        conversationId: 'c1',
        seq: 2,
        fromUserId: 'npc1',
        msgType: 1,
        status: 1,
        content: { text: '第二条' },
        replyToId: '0',
        replyToPreview: '',
        editCount: 0,
        editedAt: 0,
        createdAt: 1800,
      },
      {
        messageId: 'm1',
        conversationId: 'c1',
        seq: 1,
        fromUserId: '1001',
        msgType: 1,
        status: 1,
        content: { text: '第一条' },
        replyToId: '0',
        replyToPreview: '',
        editCount: 0,
        editedAt: 0,
        createdAt: 1000,
      },
    ],
    nextCursor: null,
    hasMore: false,
    total: 2,
  };
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderWorkspace(initialPath = '/home') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route path="home" element={<ConversationWorkspace />}>
            <Route index element={null} />
            <Route path=":conversationId" element={null} />
          </Route>
        </Routes>
        <LocationProbe />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('ConversationWorkspace 数据流', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ isLogin: true, user: ME });
    useWorkspaceStore.setState({ isDetailPanelOpen: true, isMobileChatOpen: false, isCreateDialogOpen: false });

    convApiMock.list.mockResolvedValue({ conversations: [conversationDto({})], total: 1 });
    convApiMock.getSettings.mockResolvedValue({ isMuted: false, isPinned: false, nickname: '' });
    convApiMock.getMembers.mockResolvedValue({
      members: [
        { userId: '1001', username: '我自己', avatar: '', role: 1, alias: '', joinedAt: 1, lastReadSeq: 2, isMuted: false, muteUntil: 0, memberType: 1, botId: '0' },
        { userId: 'npc1', username: '林川', avatar: '', role: 0, alias: '', joinedAt: 1, lastReadSeq: 2, isMuted: false, muteUntil: 0, memberType: 1, botId: '0' },
      ],
      total: 2,
    });
    convApiMock.markRead.mockResolvedValue(undefined);
    messageApiMock.list.mockResolvedValue(messagesPage());
    messageApiMock.send.mockResolvedValue({ messageId: 'm3', seq: 3, createdAt: 2500 });
  });

  it('renders the conversation list with preview and unread badge', async () => {
    renderWorkspace();
    const sidebar = await screen.findByTestId('conversation-sidebar');
    await waitFor(() => expect(within(sidebar).getByText('评审组')).toBeInTheDocument());
    expect(within(sidebar).getByText('第二条')).toBeInTheDocument();
    expect(within(sidebar).getByLabelText('未读 1 条')).toBeInTheDocument();
  });

  it('selecting a conversation routes to /home/:id and renders history', async () => {
    renderWorkspace();
    const sidebar = await screen.findByTestId('conversation-sidebar');
    await waitFor(() => expect(within(sidebar).getByText('评审组')).toBeInTheDocument());

    fireEvent.click(within(sidebar).getByText('评审组'));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/home/c1'));

    await waitFor(() => expect(screen.getByText('第一条')).toBeInTheDocument());
    expect(screen.getByText('第二条')).toBeInTheDocument();
    // 群聊中他人消息展示发送者名。
    expect(screen.getAllByText('林川').length).toBeGreaterThan(0);
  });

  it('sends a message optimistically and reconciles with the server ack', async () => {
    renderWorkspace('/home/c1');
    await waitFor(() => expect(screen.getByText('第二条')).toBeInTheDocument());

    const input = screen.getByLabelText('输入消息');
    fireEvent.change(input, { target: { value: '新消息内容' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    // 乐观气泡立即出现。
    await waitFor(() => expect(screen.getByText('新消息内容')).toBeInTheDocument());
    await waitFor(() =>
      expect(messageApiMock.send).toHaveBeenCalledWith(
        expect.objectContaining({
          conversationId: 'c1',
          msgType: 1,
          content: { text: '新消息内容' },
          clientMsgId: expect.stringContaining('c-'),
        }),
      ),
    );
    // 服务端确认后消息仍在（占位 → 正式）。
    await waitFor(() => expect(screen.getByText('新消息内容')).toBeInTheDocument());
  });

  it('marks the active conversation as read', async () => {
    renderWorkspace('/home/c1');
    await waitFor(() => expect(screen.getByText('第二条')).toBeInTheDocument());
    await waitFor(() => expect(convApiMock.markRead).toHaveBeenCalledWith('c1', 2), { timeout: 2000 });
  });
});
