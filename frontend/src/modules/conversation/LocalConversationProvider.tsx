import { createContext, useContext, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { createInitialMessages, initialConversations } from './demoData';
import type { ConversationSummary, MessagesByConversation, TextMessage } from './types';

export interface LocalConversationContextValue {
  conversations: ConversationSummary[];
  activeConversationId: string;
  activeConversation: ConversationSummary;
  activeMessages: TextMessage[];
  isMobileChatOpen: boolean;
  selectConversation: (conversationId: string) => void;
  returnToConversationList: () => void;
  sendTextMessage: (text: string) => void;
}

function getInitialActiveConversation(): ConversationSummary {
  const conversation = initialConversations.find((item) => item.id === 'conv-linchuan');
  if (!conversation) {
    throw new Error('The local conversation seed must include conv-linchuan.');
  }

  return conversation;
}

const initialActiveConversation = getInitialActiveConversation();

const LocalConversationContext = createContext<LocalConversationContextValue | null>(null);

function createInitialConversations(): ConversationSummary[] {
  return initialConversations.map((conversation) => ({ ...conversation }));
}

export function LocalConversationProvider({ children }: { children: ReactNode }): React.JSX.Element {
  const [conversations, setConversations] = useState(createInitialConversations);
  const [messagesByConversation, setMessagesByConversation] = useState<MessagesByConversation>(
    createInitialMessages,
  );
  const [activeConversationId, setActiveConversationId] = useState('conv-linchuan');
  const [isMobileChatOpen, setIsMobileChatOpen] = useState(false);
  const localMessageCounter = useRef(0);

  const activeConversation =
    conversations.find((conversation) => conversation.id === activeConversationId) ?? initialActiveConversation;
  const activeMessages = useMemo(
    () => messagesByConversation[activeConversation.id] ?? [],
    [activeConversation.id, messagesByConversation],
  );

  const value = useMemo<LocalConversationContextValue>(
    () => ({
      conversations,
      activeConversationId,
      activeConversation,
      activeMessages,
      isMobileChatOpen,
      selectConversation: (conversationId) => {
        if (!conversations.some((conversation) => conversation.id === conversationId)) {
          return;
        }

        setActiveConversationId(conversationId);
        setConversations((currentConversations) =>
          currentConversations.map((conversation) =>
            conversation.id === conversationId ? { ...conversation, unreadCount: 0 } : conversation,
          ),
        );
        setIsMobileChatOpen(true);
      },
      returnToConversationList: () => {
        setIsMobileChatOpen(false);
      },
      sendTextMessage: (text) => {
        const preview = text.trim();
        if (!preview) {
          return;
        }

        const createdAt = Date.now();
        localMessageCounter.current += 1;
        const localId = `local-${createdAt}-${localMessageCounter.current}`;
        const message: TextMessage = {
          id: localId,
          clientMsgId: localId,
          conversationId: activeConversation.id,
          seq: localId,
          senderId: 'local-user',
          senderName: '\u6211',
          direction: 'outgoing',
          msgType: 1,
          content: { text },
          createdAt,
        };

        setMessagesByConversation((currentMessages) => ({
          ...currentMessages,
          [activeConversation.id]: [...(currentMessages[activeConversation.id] ?? []), message],
        }));
        setConversations((currentConversations) =>
          currentConversations.map((conversation) =>
            conversation.id === activeConversation.id
              ? { ...conversation, lastMessagePreview: preview, lastMessageAt: createdAt }
              : conversation,
          ),
        );
      },
    }),
    [activeConversation, activeConversationId, activeMessages, conversations, isMobileChatOpen],
  );

  return <LocalConversationContext.Provider value={value}>{children}</LocalConversationContext.Provider>;
}

// The hook intentionally shares this module's public Provider API.
// eslint-disable-next-line react-refresh/only-export-components
export function useLocalConversation(): LocalConversationContextValue {
  const context = useContext(LocalConversationContext);
  if (!context) {
    throw new Error('useLocalConversation must be used within LocalConversationProvider.');
  }

  return context;
}
