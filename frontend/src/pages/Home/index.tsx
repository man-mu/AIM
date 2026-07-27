import { ConversationWorkspace } from '@/modules/conversation/ConversationWorkspace';

/** 消息工作台页（/home 与 /home/:conversationId 共用）。 */
export default function Home(): React.JSX.Element {
  return <ConversationWorkspace />;
}
