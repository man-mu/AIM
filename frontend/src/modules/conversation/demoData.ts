import type { ConversationSummary, MessagesByConversation } from './types';

export const initialConversations: ConversationSummary[] = [
  {
    id: 'conv-linchuan',
    type: 'direct',
    name: '\u6797\u5ddd',
    avatar: '',
    memberCount: 2,
    presence: 'online',
    announcement: '',
    lastMessagePreview: '\u665a\u4e0a\u4e00\u8d77\u5403\u996d\u5417\uff1f',
    lastMessageAt: 1_770_000_000_000,
    unreadCount: 0,
    isPinned: true,
  },
  {
    id: 'conv-weekend',
    type: 'group',
    name: '\u5468\u672b\u8bfb\u4e66\u4f1a',
    avatar: '',
    memberCount: 18,
    presence: null,
    announcement: '\u6bcf\u5468\u516d\u4e0b\u5348\u5171\u8bfb\u3002',
    lastMessagePreview: '\u6b22\u8fce\u52a0\u5165\u672c\u5468\u7684\u8bfb\u4e66\u4f1a\u3002',
    lastMessageAt: 1_770_000_100_000,
    unreadCount: 2,
    isPinned: false,
  },
];

const initialMessages: MessagesByConversation = {
  'conv-linchuan': [
    {
      id: 'seed-linchuan-1',
      clientMsgId: 'seed-linchuan-1',
      conversationId: 'conv-linchuan',
      seq: '1',
      senderId: 'user-linchuan',
      senderName: '\u6797\u5ddd',
      direction: 'incoming',
      msgType: 1,
      content: { text: '\u665a\u4e0a\u4e00\u8d77\u5403\u996d\u5417\uff1f' },
      createdAt: 1_770_000_000_000,
    },
  ],
  'conv-weekend': [
    {
      id: 'seed-weekend-1',
      clientMsgId: 'seed-weekend-1',
      conversationId: 'conv-weekend',
      seq: '1',
      senderId: 'user-reader-1',
      senderName: '\u8bfb\u4e66\u4f1a\u7ba1\u7406\u5458',
      direction: 'incoming',
      msgType: 1,
      content: { text: '\u6b22\u8fce\u52a0\u5165\u672c\u5468\u7684\u8bfb\u4e66\u4f1a\u3002' },
      createdAt: 1_770_000_100_000,
    },
  ],
};

export function createInitialMessages(): MessagesByConversation {
  return Object.fromEntries(
    Object.entries(initialMessages).map(([conversationId, messages]) => [
      conversationId,
      messages.map((message) => ({ ...message, content: { ...message.content } })),
    ]),
  );
}
