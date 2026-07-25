import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ChatPanel } from './components/ChatPanel';
import { ConversationDetailPanel } from './components/ConversationDetailPanel';
import { ConversationList } from './components/ConversationList';
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

function MobileChatOpenProbe() {
  const { isMobileChatOpen } = useLocalConversation();

  return <p data-testid="mobile-chat-open">{String(isMobileChatOpen)}</p>;
}

function renderConversationWorkspace() {
  return render(
    <LocalConversationProvider>
      <ConversationList />
      <ChatPanel />
      <ConversationDetailPanel />
      <MobileChatOpenProbe />
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
    expect(screen.getByTestId('active-preview').textContent).toBe('\u6700\u540e\u4e00\u6761');

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

describe('Conversation workspace components', () => {
  it('selects the book club, clears its unread badge, and updates its detail panel', () => {
    renderConversationWorkspace();

    fireEvent.click(screen.getByRole('button', { name: /\u5468\u672b\u8bfb\u4e66\u4f1a/ }));

    expect(
      within(screen.getByLabelText('\u6d88\u606f\u8bb0\u5f55')).getByText('\u6b22\u8fce\u52a0\u5165\u672c\u5468\u7684\u8bfb\u4e66\u4f1a\u3002'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('\u672a\u8bfb 2 \u6761')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '\u5468\u672b\u8bfb\u4e66\u4f1a' })).toBeInTheDocument();
    expect(within(screen.getByLabelText('\u4f1a\u8bdd\u8be6\u60c5')).getByText('3 \u4f4d\u6210\u5458')).toBeInTheDocument();
  });

  it('sends a message with Enter, updates the preview, and clears the composer', () => {
    renderConversationWorkspace();
    fireEvent.click(screen.getByRole('button', { name: /\u5468\u672b\u8bfb\u4e66\u4f1a/ }));

    const messageList = screen.getByLabelText('\u6d88\u606f\u8bb0\u5f55');
    const messageCount = messageList.querySelectorAll('article').length;
    const composer = screen.getByRole('textbox', { name: '\u8f93\u5165\u6d88\u606f' });
    fireEvent.change(composer, { target: { value: '\u4eca\u665a\u89c1' } });
    const enterEvent = new KeyboardEvent('keydown', {
      bubbles: true,
      cancelable: true,
      code: 'Enter',
      key: 'Enter',
    });
    fireEvent(composer, enterEvent);

    expect(enterEvent.defaultPrevented).toBe(true);
    expect(messageList.querySelectorAll('article')).toHaveLength(messageCount + 1);
    expect(within(messageList).getAllByText('\u4eca\u665a\u89c1')).toHaveLength(1);
    expect(within(screen.getByRole('button', { name: /\u5468\u672b\u8bfb\u4e66\u4f1a/ })).getByText('\u4eca\u665a\u89c1')).toBeInTheDocument();
    expect(composer).toHaveValue('');
  });

  it('keeps a newline in the composer when Shift+Enter is pressed', () => {
    renderConversationWorkspace();

    const messageList = screen.getByLabelText('\u6d88\u606f\u8bb0\u5f55');
    const messageCount = messageList.querySelectorAll('article').length;
    const composer = screen.getByRole('textbox', { name: '\u8f93\u5165\u6d88\u606f' });
    fireEvent.change(composer, { target: { value: '\u7b2c\u4e00\u884c' } });
    const shiftEnterEvent = new KeyboardEvent('keydown', {
      bubbles: true,
      cancelable: true,
      code: 'Enter',
      key: 'Enter',
      shiftKey: true,
    });
    fireEvent(composer, shiftEnterEvent);
    expect(shiftEnterEvent.defaultPrevented).toBe(false);
    fireEvent.change(composer, { target: { value: '\u7b2c\u4e00\u884c\n' } });

    expect(composer).toHaveValue('\u7b2c\u4e00\u884c\n');
    expect(messageList.querySelectorAll('article')).toHaveLength(messageCount);
  });

  it('provides wrapping constraints for long message tokens and group announcements', () => {
    renderConversationWorkspace();
    fireEvent.click(screen.getByRole('button', { name: /\u5468\u672b\u8bfb\u4e66\u4f1a/ }));

    const messageBubble = screen.getByLabelText('\u6d88\u606f\u8bb0\u5f55').querySelector('article > p');
    const announcement = within(screen.getByLabelText('\u4f1a\u8bdd\u8be6\u60c5')).getByText('\u6bcf\u5468\u516d\u4e0b\u5348\u5171\u8bfb\u3002');

    expect(messageBubble).toHaveClass('min-w-0', 'break-words');
    expect(announcement).toHaveClass('min-w-0', 'break-words');
  });

  it('falls back from a whitespace avatar to the conversation name initial', () => {
    renderConversationWorkspace();

    const listAvatar = screen
      .getByLabelText('\u4f1a\u8bdd\u5217\u8868')
      .querySelector('button[aria-current="true"] > span');
    const detailAvatar = screen.getByLabelText('\u4f1a\u8bdd\u8be6\u60c5').querySelector('div > span');

    expect(listAvatar).toHaveTextContent('\u6797');
    expect(detailAvatar).toHaveTextContent('\u6797');
  });

  it('disables blank sends and does not add a message on form submit', () => {
    renderConversationWorkspace();

    const messageList = screen.getByLabelText('\u6d88\u606f\u8bb0\u5f55');
    const messageCount = messageList.querySelectorAll('li').length;
    const composer = screen.getByRole('textbox', { name: '\u8f93\u5165\u6d88\u606f' });
    fireEvent.change(composer, { target: { value: '  \n  ' } });

    expect(screen.getByRole('button', { name: '\u53d1\u9001\u6d88\u606f' })).toBeDisabled();
    fireEvent.submit(composer.closest('form') as HTMLFormElement);

    expect(messageList.querySelectorAll('li')).toHaveLength(messageCount);
  });

  it('returns to the conversation list from the mobile back icon', () => {
    renderConversationWorkspace();
    fireEvent.click(screen.getByRole('button', { name: /\u5468\u672b\u8bfb\u4e66\u4f1a/ }));

    fireEvent.click(screen.getByRole('button', { name: '\u8fd4\u56de\u4f1a\u8bdd\u5217\u8868' }));

    expect(screen.getByTestId('mobile-chat-open')).toHaveTextContent('false');
  });
});
