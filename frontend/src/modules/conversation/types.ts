export type ConversationType = 'direct' | 'group';

export interface ConversationSummary {
  id: string;
  type: ConversationType;
  name: string;
  avatar: string;
  memberCount: number;
  presence: 'online' | 'offline' | null;
  announcement: string;
  lastMessagePreview: string;
  lastMessageAt: number;
  unreadCount: number;
  isPinned: boolean;
}

export interface TextMessage {
  id: string;
  clientMsgId: string;
  conversationId: string;
  seq: string;
  senderId: string;
  senderName: string;
  direction: 'incoming' | 'outgoing';
  msgType: 1;
  content: { text: string };
  createdAt: number;
}

export type MessagesByConversation = Record<string, TextMessage[]>;
