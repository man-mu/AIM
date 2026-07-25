import { useRef, useState } from 'react';
import HomeShell, { type HomeShellProps } from '@/components/Home/HomeShell';
import { useLocalLogout } from '@/hooks/useAuth';
import { useUser } from '@/hooks/useUser';
import {
  LocalConversationProvider,
  useLocalConversation,
} from '@/modules/conversation/LocalConversationProvider';
import { ChatPanel } from '@/modules/conversation/components/ChatPanel';
import { ConversationDetailPanel } from '@/modules/conversation/components/ConversationDetailPanel';
import { ConversationList } from '@/modules/conversation/components/ConversationList';
import { useAuthStore } from '@/stores/useAuthStore';

type ConversationWorkspaceProps = Omit<
  HomeShellProps,
  'isMobileChatOpen' | 'sidebarContent' | 'chatContent' | 'detailContent'
>;

function ConversationWorkspace(props: ConversationWorkspaceProps): React.JSX.Element {
  const { isMobileChatOpen } = useLocalConversation();

  return (
    <HomeShell
      {...props}
      isMobileChatOpen={isMobileChatOpen}
      sidebarContent={<ConversationList />}
      chatContent={<ChatPanel />}
      detailContent={<ConversationDetailPanel />}
    />
  );
}

export default function Home() {
  const cachedUser = useAuthStore((state) => state.user);
  const userQuery = useUser();
  const localLogout = useLocalLogout();
  const logoutStarted = useRef(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = () => {
    if (logoutStarted.current) {
      return;
    }

    logoutStarted.current = true;
    setIsLoggingOut(true);
    localLogout.mutate();
  };

  return (
    <LocalConversationProvider>
      <ConversationWorkspace
        user={userQuery.data ?? cachedUser}
        isUserLoading={userQuery.isLoading && !cachedUser}
        isLoggingOut={isLoggingOut || localLogout.isPending}
        onLogout={handleLogout}
      />
    </LocalConversationProvider>
  );
}
