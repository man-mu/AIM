import { SendOutlined } from '@ant-design/icons';
import { useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { useLocalConversation } from '../LocalConversationProvider';

export function MessageComposer(): React.JSX.Element {
  const { sendTextMessage } = useLocalConversation();
  const [value, setValue] = useState('');
  const canSend = value.trim().length > 0;

  const submitMessage = () => {
    if (!value.trim()) {
      return;
    }

    sendTextMessage(value);
    setValue('');
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    submitMessage();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submitMessage();
    }
  };

  return (
    <form onSubmit={handleSubmit} className="border-t border-black/[0.08] bg-white px-4 py-3 sm:px-6">
      <div className="flex items-end gap-2 rounded-lg border border-black/[0.12] bg-[#fbfbfd] px-3 py-2 focus-within:border-[#0071e3]">
        <textarea
          aria-label="输入消息"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={1}
          placeholder="输入消息"
          className="min-h-6 flex-1 resize-none bg-transparent text-sm leading-6 text-[#1d1d1f] outline-none placeholder:text-[#86868b]"
        />
        <button
          type="submit"
          aria-label="发送消息"
          disabled={!canSend}
          className="grid size-8 shrink-0 place-items-center rounded-md bg-[#0071e3] text-white transition hover:bg-[#0077ed] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] disabled:cursor-not-allowed disabled:bg-[#d2d2d7]"
        >
          <SendOutlined aria-hidden />
        </button>
      </div>
    </form>
  );
}
