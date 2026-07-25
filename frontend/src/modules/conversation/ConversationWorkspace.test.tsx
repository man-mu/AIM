import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
  LocalConversationProvider,
  useLocalConversation,
} from './LocalConversationProvider';

function ConversationWorkspaceProbe() {
  const {
    activeConversation,
    activeMessages,
    conversations,
    isMobileChatOpen,
    returnToConversationList,
    selectConversation,
    sendTextMessage,
  } = useLocalConversation();

  return (
    <>
      <p data-testid="active-name">{activeConversation.name}</p>
      <p data-testid="active-unread">{activeConversation.unreadCount}</p>
      <p data-testid="active-preview">{activeConversation.lastMessagePreview}</p>
      <p data-testid="message-count">{activeMessages.length}</p>
      <p data-testid="latest-local-messages">{JSON.stringify(activeMessages.slice(-2))}</p>
      <p data-testid="mobile-chat-open">{String(isMobileChatOpen)}</p>
      <button type="button" onClick={() => selectConversation('conv-weekend')}>
        {'\u9009\u62e9\u5468\u672b\u8bfb\u4e66\u4f1a'}
      </button>
      <button type="button" onClick={returnToConversationList}>
        {'\u8fd4\u56de\u5217\u8868'}
      </button>
      <button type="button" onClick={() => sendTextMessage('\u672c\u5730\u6d88\u606f')}>
        {'\u53d1\u9001\u672c\u5730\u6d88\u606f'}
      </button>
      <button type="button" onClick={() => sendTextMessage('  \u7b2c\u4e00\u884c\n\u7b2c\u4e8c\u884c  ')}>
        {'\u53d1\u9001\u591a\u884c\u6d88\u606f'}
      </button>
      <button type="button" onClick={() => sendTextMessage('  \u6700\u540e\u4e00\u6761  ')}>
        {'\u53d1\u9001\u6700\u540e\u6d88\u606f'}
      </button>
      <button type="button" onClick={() => sendTextMessage('  \n  ')}>
        {'\u53d1\u9001\u7a7a\u767d\u6d88\u606f'}
      </button>
      <p data-testid="weekend-unread">
        {conversations.find((conversation) => conversation.id === 'conv-weekend')?.unreadCount}
      </p>
    </>
  );
}

function renderWorkspace() {
  return render(
    <LocalConversationProvider>
      <ConversationWorkspaceProbe />
    </LocalConversationProvider>,
  );
}

describe('LocalConversationProvider', () => {
  it('selects the weekend book club, clears its unread count, and opens mobile chat', () => {
    renderWorkspace();

    fireEvent.click(screen.getByRole('button', { name: '\u9009\u62e9\u5468\u672b\u8bfb\u4e66\u4f1a' }));

    expect(screen.getByTestId('active-name')).toHaveTextContent('\u5468\u672b\u8bfb\u4e66\u4f1a');
    expect(screen.getByTestId('active-unread')).toHaveTextContent('0');
    expect(screen.getByTestId('weekend-unread')).toHaveTextContent('0');
    expect(screen.getByTestId('mobile-chat-open')).toHaveTextContent('true');
  });

  it('updates the active conversation preview when sending local text', () => {
    renderWorkspace();

    fireEvent.click(screen.getByRole('button', { name: '\u9009\u62e9\u5468\u672b\u8bfb\u4e66\u4f1a' }));
    fireEvent.click(screen.getByRole('button', { name: '\u53d1\u9001\u672c\u5730\u6d88\u606f' }));

    expect(screen.getByTestId('active-preview')).toHaveTextContent('\u672c\u5730\u6d88\u606f');
    expect(screen.getByTestId('message-count')).toHaveTextContent('2');
  });

  it('keeps local pending messages distinct and preserves their original text', () => {
    const dateNow = vi.spyOn(Date, 'now').mockReturnValue(1_770_000_200_000);
    renderWorkspace();

    fireEvent.click(screen.getByRole('button', { name: '\u53d1\u9001\u591a\u884c\u6d88\u606f' }));
    fireEvent.click(screen.getByRole('button', { name: '\u53d1\u9001\u6700\u540e\u6d88\u606f' }));

    const messages = JSON.parse(screen.getByTestId('latest-local-messages').textContent ?? '[]') as Array<{
      id: string;
      clientMsgId: string;
      conversationId: string;
      seq: string | null;
      content: { text: string };
    }>;

    expect(messages).toHaveLength(2);
    expect(messages.map((message) => message.id)).toEqual([
      'local-1770000200000-1',
      'local-1770000200000-2',
    ]);
    expect(new Set(messages.map((message) => message.clientMsgId)).size).toBe(2);
    expect(messages.every((message) => message.id.startsWith('local-'))).toBe(true);
    expect(messages.every((message) => message.clientMsgId.startsWith('local-'))).toBe(true);
    expect(messages.every((message) => message.conversationId === 'conv-linchuan')).toBe(true);
    expect(messages.every((message) => message.seq === null)).toBe(true);
    expect(messages[0]?.content.text).toBe('  \u7b2c\u4e00\u884c\n\u7b2c\u4e8c\u884c  ');
    expect(screen.getByTestId('active-preview')).toHaveTextContent('\u6700\u540e\u4e00\u6761');

    dateNow.mockRestore();
  });

  it('leaves the active messages and preview unchanged for blank text', () => {
    renderWorkspace();

    fireEvent.click(screen.getByRole('button', { name: '\u9009\u62e9\u5468\u672b\u8bfb\u4e66\u4f1a' }));
    const initialMessageCount = screen.getByTestId('message-count').textContent;
    const initialPreview = screen.getByTestId('active-preview').textContent;
    fireEvent.click(screen.getByRole('button', { name: '\u53d1\u9001\u7a7a\u767d\u6d88\u606f' }));

    expect(screen.getByTestId('message-count')).toHaveTextContent(initialMessageCount ?? '');
    expect(screen.getByTestId('active-preview')).toHaveTextContent(initialPreview ?? '');
  });

  it('closes the mobile chat when returning to the conversation list', () => {
    renderWorkspace();

    fireEvent.click(screen.getByRole('button', { name: '\u9009\u62e9\u5468\u672b\u8bfb\u4e66\u4f1a' }));
    fireEvent.click(screen.getByRole('button', { name: '\u8fd4\u56de\u5217\u8868' }));

    expect(screen.getByTestId('mobile-chat-open')).toHaveTextContent('false');
  });
});
