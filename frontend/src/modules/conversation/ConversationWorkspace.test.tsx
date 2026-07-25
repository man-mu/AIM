import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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
