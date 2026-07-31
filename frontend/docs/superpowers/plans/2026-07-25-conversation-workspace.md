# Conversation Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a responsive `/home` conversation workspace that switches between seeded conversations and immediately renders locally sent text messages without changing network mocks.

**Architecture:** Keep `HomeShell` as the authenticated application frame and pass the conversation list, chat content, and details through explicit slots. Add `src/modules/conversation/` with a local Context provider that owns selected conversation, mobile panel state, and per-conversation text messages; all seeded display data remains inside the module, outside `src/mocks`.

**Tech Stack:** React 19, TypeScript, Tailwind CSS v4, Ant Design icons/tooltip, Vitest, React Testing Library.

---

## Planned Files

| Path | Responsibility |
|---|---|
| `src/modules/conversation/types.ts` | String-ID conversation and text-message UI models aligned with the API concepts. |
| `src/modules/conversation/demoData.ts` | Immutable in-module seeded conversations and message histories; no HTTP handler registration. |
| `src/modules/conversation/LocalConversationProvider.tsx` | Local selection, unread clearing, mobile mode, and immediate-send state transitions. |
| `src/modules/conversation/components/ConversationList.tsx` | Selectable conversation summary list. |
| `src/modules/conversation/components/ChatPanel.tsx` | Active conversation header, mobile back action, messages, and composer composition. |
| `src/modules/conversation/components/MessageList.tsx` | Accessible, direction-aware text bubbles and auto-scroll target. |
| `src/modules/conversation/components/MessageComposer.tsx` | Controlled textarea with Enter-send and Shift+Enter newline behavior. |
| `src/modules/conversation/components/ConversationDetailPanel.tsx` | Read-only direct/group conversation summary. |
| `src/modules/conversation/ConversationWorkspace.test.tsx` | Component-level tests for selection, unread clearing, sending, blank content, and newline behavior. |
| `src/components/Home/HomeShell.tsx` | Slot-based responsive frame with preserved account and logout behavior. |
| `src/components/Home/HomeShell.test.tsx` | Regression coverage for shell slots, account skeleton, and logout. |
| `src/pages/Home/index.tsx` | Wires the local provider and conversation slots to the authenticated shell. |
| `src/pages/Home/index.test.tsx` | Retains auth/logout regression coverage while asserting the initial workspace renders. |

### Task 1: Define and Test Local Conversation State

**Files:**
- Create: `src/modules/conversation/ConversationWorkspace.test.tsx`
- Create: `src/modules/conversation/types.ts`
- Create: `src/modules/conversation/demoData.ts`
- Create: `src/modules/conversation/LocalConversationProvider.tsx`

- [ ] **Step 1: Write the failing provider behavior test**

Create the test file with a probe that consumes the provider and exposes the behavior without depending on presentational components:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import {
  LocalConversationProvider,
  useLocalConversation,
} from './LocalConversationProvider';

function Probe() {
  const {
    activeConversation,
    conversations,
    selectConversation,
    sendTextMessage,
  } = useLocalConversation();
  const unread = conversations.find((item) => item.id === 'conv-weekend')?.unreadCount;

  return (
    <>
      <p data-testid="active-conversation">{activeConversation.name}</p>
      <p data-testid="weekend-unread">{unread}</p>
      <button type="button" onClick={() => selectConversation('conv-weekend')}>switch</button>
      <button type="button" onClick={() => sendTextMessage('本地消息')}>send</button>
      <p data-testid="preview">{conversations.find((item) => item.id === 'conv-weekend')?.lastMessagePreview}</p>
    </>
  );
}

describe('LocalConversationProvider', () => {
  it('switches conversations, clears unread count, and immediately updates the local preview', () => {
    render(<LocalConversationProvider><Probe /></LocalConversationProvider>);

    fireEvent.click(screen.getByRole('button', { name: 'switch' }));
    fireEvent.click(screen.getByRole('button', { name: 'send' }));

    expect(screen.getByTestId('active-conversation')).toHaveTextContent('周末读书会');
    expect(screen.getByTestId('weekend-unread')).toHaveTextContent('0');
    expect(screen.getByTestId('preview')).toHaveTextContent('本地消息');
  });
});
```

- [ ] **Step 2: Verify the test fails before implementation**

Run: `pnpm test -- src/modules/conversation/ConversationWorkspace.test.tsx`

Expected: FAIL because `./LocalConversationProvider` does not exist.

- [ ] **Step 3: Add string-ID models, immutable demo data, and the provider**

Create `types.ts` with these exact interfaces. Do not use `number` for any identifier or sequence:

```ts
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
```

Create at least `conv-linchuan` and `conv-weekend` in `demoData.ts`; give `conv-weekend` the name `周末读书会`, an initial `unreadCount: 2`, and an incoming text message whose `content.text` is `欢迎加入本周的读书会。`. Export a clone function so provider state never mutates imported seed arrays:

```ts
export function createInitialMessages(): MessagesByConversation {
  return Object.fromEntries(
    Object.entries(demoMessagesByConversation).map(([conversationId, messages]) => [
      conversationId,
      messages.map((message) => ({ ...message, content: { ...message.content } })),
    ]),
  ) as MessagesByConversation;
}
```

Implement the provider with this public contract:

```ts
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
```

`selectConversation` must set the active ID, set that conversation's `unreadCount` to `0`, and open the mobile chat. `sendTextMessage` must trim whitespace, return without changing state for an empty result, and otherwise append one outgoing message using `id`, `seq`, and `clientMsgId` in the `local-<timestamp>` format. It must update only the active conversation's `lastMessagePreview` and `lastMessageAt`.

- [ ] **Step 4: Verify provider behavior passes**

Run: `pnpm test -- src/modules/conversation/ConversationWorkspace.test.tsx`

Expected: PASS with one provider behavior test.

- [ ] **Step 5: Commit the state boundary**

```bash
git add src/modules/conversation/types.ts src/modules/conversation/demoData.ts src/modules/conversation/LocalConversationProvider.tsx src/modules/conversation/ConversationWorkspace.test.tsx
git commit -m "feat(conversation): add local workspace state"
```

### Task 2: Convert the Home Shell to Explicit Slots

**Files:**
- Modify: `src/components/Home/HomeShell.test.tsx`
- Modify: `src/components/Home/HomeShell.tsx`

- [ ] **Step 1: Write the failing slot regression test**

Replace the old empty-state expectation in the first `HomeShell` test with slot content and retain its logout assertion:

```tsx
render(
  <HomeShell
    user={user}
    isUserLoading={false}
    isLoggingOut={false}
    isMobileChatOpen={false}
    sidebarContent={<p>会话列表</p>}
    chatContent={<h1>林川</h1>}
    detailContent={<p>会话详情</p>}
    onLogout={onLogout}
  />,
);

expect(screen.getByText('会话列表')).toBeInTheDocument();
expect(screen.getByRole('heading', { name: '林川' })).toBeInTheDocument();
expect(screen.getByText('会话详情')).toBeInTheDocument();
```

Apply the same required props to the loading test.

- [ ] **Step 2: Verify the slot test fails**

Run: `pnpm test -- src/components/Home/HomeShell.test.tsx`

Expected: FAIL because `HomeShellProps` does not yet define slot props.

- [ ] **Step 3: Implement the slot-based shell**

Add these props to `HomeShellProps`:

```ts
import type { ReactNode } from 'react';

export interface HomeShellProps {
  user: UserInfo | null;
  isUserLoading: boolean;
  isLoggingOut: boolean;
  isMobileChatOpen: boolean;
  sidebarContent: ReactNode;
  chatContent: ReactNode;
  detailContent: ReactNode;
  onLogout: () => void;
}
```

Keep AIM navigation, account rendering, skeleton, and the logout button in the left column. Replace the empty-state center section with `chatContent`, append `sidebarContent` below primary navigation, and replace the static right text with `detailContent`.

Apply responsive classes so the left column is hidden only below `sm` while `isMobileChatOpen` is true, the chat column is hidden only below `sm` while it is false, and the detail column is `hidden lg:block`. Preserve the existing `min-h-screen`, light background, and keyboard focus styling.

- [ ] **Step 4: Verify shell tests pass**

Run: `pnpm test -- src/components/Home/HomeShell.test.tsx`

Expected: PASS with slot, account skeleton, and disabled logout coverage.

- [ ] **Step 5: Commit the shell refactor**

```bash
git add src/components/Home/HomeShell.tsx src/components/Home/HomeShell.test.tsx
git commit -m "refactor(home): expose workspace content slots"
```

### Task 3: Build and Test the Conversation Panels

**Files:**
- Modify: `src/modules/conversation/ConversationWorkspace.test.tsx`
- Create: `src/modules/conversation/components/ConversationList.tsx`
- Create: `src/modules/conversation/components/MessageList.tsx`
- Create: `src/modules/conversation/components/MessageComposer.tsx`
- Create: `src/modules/conversation/components/ChatPanel.tsx`
- Create: `src/modules/conversation/components/ConversationDetailPanel.tsx`

- [ ] **Step 1: Extend the failing workspace test with user-visible behavior**

Add a complete panel probe that renders the five components inside the provider. Assert conversation switching, immediate text rendering, empty submit prevention, and Shift+Enter:

```tsx
render(
  <LocalConversationProvider>
    <ConversationList />
    <ChatPanel />
    <ConversationDetailPanel />
  </LocalConversationProvider>,
);

fireEvent.click(screen.getByRole('button', { name: /周末读书会/ }));
expect(screen.getByText('欢迎加入本周的读书会。')).toBeInTheDocument();
expect(screen.queryByLabelText('未读 2 条')).not.toBeInTheDocument();

const composer = screen.getByRole('textbox', { name: '输入消息' });
fireEvent.change(composer, { target: { value: '今晚见' } });
fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter' });
expect(screen.getByText('今晚见')).toBeInTheDocument();

fireEvent.change(composer, { target: { value: '第一行' } });
fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter', shiftKey: true });
expect(composer).toHaveValue('第一行\n');

fireEvent.change(composer, { target: { value: '   ' } });
expect(screen.getByRole('button', { name: '发送消息' })).toBeDisabled();
```

Use a unique accessible name for the unread badge, such as `未读 2 条`, so the test does not depend on a bare number.

- [ ] **Step 2: Verify the panel test fails**

Run: `pnpm test -- src/modules/conversation/ConversationWorkspace.test.tsx`

Expected: FAIL because the panel component imports do not exist.

- [ ] **Step 3: Implement the list and detail panel**

`ConversationList` must render one button per conversation. Its button accessible name must include the name and nonzero unread count; its click handler must call `selectConversation(conversation.id)`. Use `aria-current="true"` for the active item. Show a small initials avatar, preview text, time, and a circular unread badge only where `unreadCount > 0`.

`ConversationDetailPanel` must read `activeConversation`. For `type === 'direct'`, show `在线` or `离线` from `presence`. For `type === 'group'`, show `${memberCount} 位成员` and an announcement section that renders `暂无公告` when the seed field is empty. It remains read-only and makes no request.

- [ ] **Step 4: Implement messages and composer**

`MessageList` must render `activeMessages` in an element with `aria-label="消息记录"`, distinguish incoming and outgoing bubbles with direction-specific alignment and color, and include a trailing `ref` anchor. On `activeMessages.length` or active conversation change, call `anchorRef.current?.scrollIntoView?.({ block: 'end' })` so a local message appears in view without failing in jsdom.

`MessageComposer` must be a `<form>` containing this controlled textarea contract:

```tsx
<textarea
  aria-label="输入消息"
  value={value}
  onChange={(event) => setValue(event.target.value)}
  onKeyDown={(event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }}
/>
```

`submit` must trim only to determine sendability, call `sendTextMessage(value)`, and clear the textarea only after a nonempty send. The submit button must be disabled when `value.trim()` is empty and use `aria-label="发送消息"`.

`ChatPanel` must compose header, `MessageList`, and `MessageComposer`. Add a mobile-only Ant Design `ArrowLeftOutlined` icon button with `Tooltip title="返回会话列表"`; it calls `returnToConversationList`. Do not create a rounded text button where this familiar icon is sufficient.

- [ ] **Step 5: Verify the workspace component tests pass**

Run: `pnpm test -- src/modules/conversation/ConversationWorkspace.test.tsx`

Expected: PASS with provider state, selection, immediate send, blank prevention, and Shift+Enter coverage.

- [ ] **Step 6: Commit the conversation UI**

```bash
git add src/modules/conversation
git commit -m "feat(conversation): build local chat workspace"
```

### Task 4: Wire the Authenticated Home Page

**Files:**
- Modify: `src/pages/Home/index.tsx`
- Modify: `src/pages/Home/index.test.tsx`

- [ ] **Step 1: Add the failing initial-workspace assertion**

In the existing `uses the cached user and delegates logout to the local action` test, add:

```tsx
expect(screen.getByRole('button', { name: /林川/ })).toBeInTheDocument();
expect(screen.getByRole('textbox', { name: '输入消息' })).toBeInTheDocument();
```

- [ ] **Step 2: Verify the page test fails**

Run: `pnpm test -- src/pages/Home/index.test.tsx`

Expected: FAIL because Home still renders the old static shell content.

- [ ] **Step 3: Compose the provider and slots in Home**

Keep all existing `useUser`, `useLocalLogout`, duplicate-logout protection, and cached-user behavior. Add a small provider child so `useLocalConversation()` is called inside `LocalConversationProvider`:

```tsx
function ConversationWorkspaceHome(props: Omit<HomeShellProps, 'isMobileChatOpen' | 'sidebarContent' | 'chatContent' | 'detailContent'>) {
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
```

Import the `HomeShellProps` type exported in Task 2. Wrap this child in `<LocalConversationProvider>` from the `Home` return path. Do not add any API, query, store, or mock call.

- [ ] **Step 4: Verify Home page tests pass**

Run: `pnpm test -- src/pages/Home/index.test.tsx`

Expected: PASS with cached user, logout, loading skeleton, pending logout, and initial workspace coverage.

- [ ] **Step 5: Commit integration**

```bash
git add src/pages/Home/index.tsx src/pages/Home/index.test.tsx
git commit -m "feat(home): compose conversation workspace"
```

### Task 5: Run Full Regression and Manual Responsive Checks

**Files:**
- Modify only if a concrete regression is found in Tasks 1-4.

- [ ] **Step 1: Run focused and full frontend validation**

Run:

```bash
pnpm test
pnpm lint
pnpm build
```

Expected: all Vitest tests pass, ESLint exits 0, and Vite production build completes successfully.

- [ ] **Step 2: Run required Maven verification and record environment results**

Run:

```bash
mvn compile
mvn test
```

Expected: compilation and tests pass when Maven is installed. In the current Windows environment, record the exact `mvn` command-not-found result as an environment blocker; do not alter frontend source to compensate.

- [ ] **Step 3: Start the development server and inspect key widths**

Run: `pnpm dev -- --host 127.0.0.1`

Open `/home` with an authenticated mock session and inspect:

- desktop: navigation/list, chat, and details columns do not overlap;
- tablet: details panel is absent and the message composer remains usable;
- mobile: list is initially visible, selecting a conversation opens chat, and the tooltip-backed back icon returns to the list;
- after sending: the new outgoing bubble, conversation preview, and latest-time label update without a network request.

- [ ] **Step 4: Commit only a concrete verification fix when needed**

If manual validation requires a code correction, add its focused regression test first, make the smallest correction, rerun the relevant focused test plus the full frontend commands, and commit the exact changed source and test files with a Conventional Commit message. If no correction is needed, do not create an empty commit.
