import { useEffect, useRef } from 'react';
import { useLocalConversation } from '../LocalConversationProvider';

function formatMessageTime(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(timestamp);
}

export function MessageList(): React.JSX.Element {
  const { activeConversation, activeMessages } = useLocalConversation();
  const anchorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    anchorRef.current?.scrollIntoView?.({ block: 'end' });
  }, [activeConversation.id, activeMessages.length]);

  return (
    <ol aria-label="消息记录" className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-5 sm:px-6">
      {activeMessages.map((message) => {
        const isOutgoing = message.direction === 'outgoing';

        return (
          <li key={message.id} className={isOutgoing ? 'flex justify-end' : 'flex justify-start'}>
            <article className={isOutgoing ? 'max-w-[78%] text-right' : 'max-w-[78%] text-left'}>
              <p
                className={
                  isOutgoing
                    ? 'rounded-2xl rounded-br-md bg-[#0071e3] px-3 py-2 text-sm leading-5 text-white whitespace-pre-wrap'
                    : 'rounded-2xl rounded-bl-md bg-[#f2f2f7] px-3 py-2 text-sm leading-5 text-[#1d1d1f] whitespace-pre-wrap'
                }
              >
                {message.content.text}
              </p>
              <time className="mt-1 block text-[11px] text-[#86868b]" dateTime={new Date(message.createdAt).toISOString()}>
                {formatMessageTime(message.createdAt)}
              </time>
            </article>
          </li>
        );
      })}
      <li aria-hidden className="h-px list-none">
        <div ref={anchorRef} />
      </li>
    </ol>
  );
}
