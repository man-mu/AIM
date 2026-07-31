# AIM Auth Home Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a low-coupling three-column AIM home shell with a usable local logout action, and align login/register styling with the same restrained system-inspired visual language.

**Architecture:** `HomeShell` is a presentation-only component whose inputs are profile data, explicit state flags, and an `onLogout` callback. `pages/Home` composes existing query/store hooks and a new local logout mutation; the presentation component never reads route, storage, API, React Query, or Zustand state. The new local mutation shares cleanup mechanics with the existing server logout hook, but does not manufacture the unavailable `tokenId` parameter.

**Tech Stack:** React 19, TypeScript, React Router 8, TanStack React Query 5, Zustand 5, Ant Design 6 icons/forms, Tailwind CSS 4, Vitest, Testing Library.

---

## File Structure

- Modify: `src/hooks/useAuth.ts` - expose a local logout mutation and share local session cleanup with the existing server logout mutation.
- Modify: `src/hooks/useAuth.test.tsx` - verify local logout clears state, cache, storage, and location.
- Create: `src/components/Home/HomeShell.tsx` - render the navigation, empty message state, account display, and logout control from props only.
- Create: `src/components/Home/HomeShell.test.tsx` - cover shell rendering, loading state, callback invocation, and disabled logout state.
- Modify: `src/pages/Home/index.tsx` - compose profile query, cached auth user, local logout mutation, and `HomeShell`.
- Modify: `src/components/Auth/AuthLayout.tsx` - provide the shared visual frame for authentication pages.
- Modify: `src/components/Auth/LoginForm.tsx` - add stable styling hooks and compact supporting copy without changing submit behavior.
- Modify: `src/components/Auth/RegisterForm.tsx` - apply the same form styling while preserving current validation and submit data.
- Modify: `src/index.css` - define application base styles, authentication form treatment, focus states, and autofill rules.

`src/hooks/useUser.ts` is intentionally excluded because it is an existing user-owned, uncommitted change.

### Task 1: Local Logout Action

**Files:**
- Modify: `src/hooks/useAuth.ts`
- Modify: `src/hooks/useAuth.test.tsx`

- [ ] **Step 1: Add a failing local-logout test**

Extend the `authApiMock` reset and import list, then add this test to `src/hooks/useAuth.test.tsx`:

```tsx
import { useLocalLogout } from './useAuth';

it('clears local session state and returns to login without calling the API', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: PropsWithChildren) => (
    <MemoryRouter initialEntries={['/home']}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      <LocationProbe />
    </MemoryRouter>
  );

  storage.setAccessToken('access-token');
  storage.setRefreshToken('refresh-token');
  storage.setAccessExpire(Date.now() + 60_000);
  storage.setRefreshExpire(Date.now() + 60_000);
  queryClient.setQueryData(['user'], authData.user);
  useAuthStore.setState({ isLogin: true, user: authData.user });

  const { result } = renderHook(() => useLocalLogout(), { wrapper: Wrapper });
  await result.current.mutateAsync();

  await waitFor(() => expect(document.querySelector('[data-testid="location"]')).toHaveTextContent('/login'));
  expect(storage.getAccessToken()).toBeNull();
  expect(storage.getRefreshToken()).toBeNull();
  expect(queryClient.getQueryData(['user'])).toBeUndefined();
  expect(useAuthStore.getState()).toMatchObject({ isLogin: false, user: null });
  expect(authApiMock.logout).not.toHaveBeenCalled();
  expect(alertSpy).toHaveBeenCalledWith('已退出登录');
});
```

- [ ] **Step 2: Run the focused test and verify that it fails because `useLocalLogout` is not exported**

Run: `pnpm test src/hooks/useAuth.test.tsx`

Expected: FAIL with an import or missing-export error for `useLocalLogout`.

- [ ] **Step 3: Implement the shared cleanup hook and local mutation**

Replace the existing logout-specific cleanup closure in `src/hooks/useAuth.ts` with the following additions, keeping `useLogin` and `useRegister` unchanged:

```tsx
function useClearLocalSession() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((state) => state.clearAuth);

  return () => {
    queryClient.clear();
    storage.clearAuth();
    clearAuth();
    navigate('/login', { replace: true });
  };
}

export const useLocalLogout = () => {
  const clearLocalSession = useClearLocalSession();

  return useMutation({
    mutationFn: async () => undefined,
    onSuccess: () => {
      clearLocalSession();
      alert('已退出登录');
    },
  });
};

export const useLogout = () => {
  const clearLocalSession = useClearLocalSession();

  return useMutation({
    mutationFn: (params: LogoutParams) => authApi.logout(params),
    onSuccess: clearLocalSession,
    onError: clearLocalSession,
  });
};
```

Keep the existing `LogoutParams` import because `useLogout` still exposes the server-backed mutation for the future backend session-ID flow.

- [ ] **Step 4: Run the focused hook test and verify it passes**

Run: `pnpm test src/hooks/useAuth.test.tsx`

Expected: PASS, including login, registration, error-feedback, and local-logout cases.

- [ ] **Step 5: Commit the authentication action**

```bash
git add src/hooks/useAuth.ts src/hooks/useAuth.test.tsx
git commit -m "feat(auth): add local logout action"
```

### Task 2: Presentation-Only Home Shell

**Files:**
- Create: `src/components/Home/HomeShell.tsx`
- Create: `src/components/Home/HomeShell.test.tsx`

- [ ] **Step 1: Add failing shell interaction tests**

Create `src/components/Home/HomeShell.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import HomeShell from './HomeShell';
import type { UserInfo } from '@/types/User/User';

const user: UserInfo = {
  id: '1234567890123456789', username: 'zhangsan', phone: '138****8000',
  email: 'zhan****@foo.com', avatar: '', gender: 0, bio: '', birthday: 0,
  createdAt: 0, updatedAt: 0, balance: 0,
};

describe('HomeShell', () => {
  it('renders the message workspace and delegates logout to its callback', () => {
    const onLogout = vi.fn();
    render(<HomeShell user={user} isUserLoading={false} isLoggingOut={false} onLogout={onLogout} />);

    expect(screen.getByRole('heading', { name: '消息' })).toBeInTheDocument();
    expect(screen.getByText('开始一段新对话')).toBeInTheDocument();
    expect(screen.getByText('zhangsan')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('uses a fixed account skeleton while profile data loads and disables a pending logout', () => {
    render(<HomeShell user={null} isUserLoading isLoggingOut onLogout={vi.fn()} />);

    expect(screen.getByTestId('account-skeleton')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '退出登录' })).toBeDisabled();
  });
});
```

- [ ] **Step 2: Run the focused shell test and verify it fails because the component does not exist**

Run: `pnpm test src/components/Home/HomeShell.test.tsx`

Expected: FAIL with a module-resolution error for `./HomeShell`.

- [ ] **Step 3: Implement the pure shell**

Create `src/components/Home/HomeShell.tsx` with a prop-only boundary and no imports from hooks, router, storage, APIs, React Query, or Zustand:

```tsx
import { BellOutlined, LogoutOutlined, MessageOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
import type { UserInfo } from '@/types/User/User';

interface HomeShellProps {
  user: UserInfo | null;
  isUserLoading: boolean;
  isLoggingOut: boolean;
  onLogout: () => void;
}

const navigationItems = [
  { label: '消息', icon: MessageOutlined, active: true },
  { label: '联系人', icon: TeamOutlined, active: false },
  { label: '通知', icon: BellOutlined, active: false },
];

function accountInitial(user: UserInfo | null): string {
  return user?.username?.trim().slice(0, 1).toUpperCase() || 'A';
}

export default function HomeShell({ user, isUserLoading, isLoggingOut, onLogout }: HomeShellProps) {
  return (
    <main className="min-h-screen bg-[#f5f5f7] p-3 text-[#1d1d1f] sm:p-5">
      <section className="mx-auto grid min-h-[calc(100vh-1.5rem)] max-w-[1440px] overflow-hidden border border-black/[0.08] bg-white shadow-[0_18px_50px_rgba(0,0,0,0.08)] sm:min-h-[calc(100vh-2.5rem)] sm:grid-cols-[248px_minmax(0,1fr)] lg:grid-cols-[248px_minmax(0,1fr)_280px]">
        <aside className="flex min-h-[244px] flex-col border-b border-black/[0.08] bg-[#fbfbfd] p-4 sm:min-h-0 sm:border-b-0 sm:border-r">
          <div className="px-2 text-lg font-semibold">AIM</div>
          <nav className="mt-8 grid gap-1" aria-label="主导航">
            {navigationItems.map(({ label, icon: Icon, active }) => (
              <div key={label} aria-current={active ? 'page' : undefined} className={active ? 'flex items-center gap-3 rounded-lg bg-black/[0.06] px-3 py-2.5 text-sm font-medium' : 'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-[#6e6e73]'}>
                <Icon aria-hidden />
                <span>{label}</span>
              </div>
            ))}
          </nav>
          <div className="mt-auto hidden border-t border-black/[0.08] pt-4 sm:block">
            {isUserLoading ? <div aria-label="正在加载账户资料" className="h-10 animate-pulse rounded-lg bg-black/[0.06]" data-testid="account-skeleton" /> : <div className="flex items-center gap-3 px-1"><span className="grid size-9 place-items-center rounded-full bg-[#0071e3] text-sm font-semibold text-white">{accountInitial(user)}</span><span className="min-w-0 truncate text-sm font-medium">{user?.username || '当前账户'}</span></div>}
          </div>
        </aside>
        <section className="relative flex min-h-[420px] flex-col p-5 sm:p-7">
          <header className="flex items-center justify-between"><h1 className="text-xl font-semibold">消息</h1><button type="button" onClick={onLogout} disabled={isLoggingOut} className="inline-flex items-center gap-2 rounded-lg border border-black/[0.12] px-3 py-2 text-sm font-medium text-[#424245] transition hover:border-black/[0.25] disabled:cursor-not-allowed disabled:opacity-50"><LogoutOutlined aria-hidden />退出登录</button></header>
          <div className="flex flex-1 flex-col items-center justify-center text-center"><span className="grid size-12 place-items-center rounded-xl border border-black/[0.1] text-[#0071e3]"><PlusOutlined aria-hidden /></span><h2 className="mt-4 text-lg font-semibold">开始一段新对话</h2><p className="mt-2 max-w-xs text-sm leading-6 text-[#6e6e73]">联系人和会话将在这里出现。</p></div>
        </section>
        <aside className="hidden border-l border-black/[0.08] bg-[#fbfbfd] p-6 lg:block"><p className="text-xs font-medium text-[#6e6e73]">当前状态</p><p className="mt-3 text-sm">你的工作区已准备就绪。</p></aside>
      </section>
    </main>
  );
}
```

- [ ] **Step 4: Run the focused component test and verify it passes**

Run: `pnpm test src/components/Home/HomeShell.test.tsx`

Expected: PASS with both callback and pending/loading cases.

- [ ] **Step 5: Commit the presentation component**

```bash
git add src/components/Home/HomeShell.tsx src/components/Home/HomeShell.test.tsx
git commit -m "feat(home): add presentation shell"
```

### Task 3: Compose the Home Container

**Files:**
- Modify: `src/pages/Home/index.tsx`

- [ ] **Step 1: Add a failing Home page integration test**

Create `src/pages/Home/index.test.tsx`:

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { UserInfo } from '@/types/User/User';

const localLogoutMutate = vi.hoisted(() => vi.fn());
const cachedUser = vi.hoisted<UserInfo>(() => ({
  id: '1234567890123456789', username: 'cached-user', phone: '138****8000',
  email: 'cached@example.com', avatar: '', gender: 0, bio: '', birthday: 0,
  createdAt: 0, updatedAt: 0, balance: 0,
}));

vi.mock('@/hooks/useUser', () => ({
  useUser: () => ({ data: undefined, isLoading: false }),
}));
vi.mock('@/hooks/useAuth', () => ({
  useLocalLogout: () => ({ mutate: localLogoutMutate, isPending: false }),
}));
vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: (selector: (state: { user: UserInfo }) => unknown) => selector({ user: cachedUser }),
}));

import Home from './index';

describe('Home', () => {
  it('uses the cached user and delegates logout to the local action', () => {
    render(<Home />);

    expect(screen.getByText('cached-user')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));
    expect(localLogoutMutate).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run the focused integration test and verify it fails because Home is still a placeholder**

Run: `pnpm test src/pages/Home/index.test.tsx`

Expected: FAIL because the placeholder Home component does not render `cached-user` or the logout control.

- [ ] **Step 3: Implement the container composition**

Replace `src/pages/Home/index.tsx` with:

```tsx
import HomeShell from '@/components/Home/HomeShell';
import { useLocalLogout } from '@/hooks/useAuth';
import { useUser } from '@/hooks/useUser';
import { useAuthStore } from '@/stores/useAuthStore';

export default function Home() {
  const cachedUser = useAuthStore((state) => state.user);
  const userQuery = useUser();
  const localLogout = useLocalLogout();

  return (
    <HomeShell
      user={userQuery.data ?? cachedUser}
      isUserLoading={userQuery.isLoading && !cachedUser}
      isLoggingOut={localLogout.isPending}
      onLogout={() => localLogout.mutate()}
    />
  );
}
```

- [ ] **Step 4: Run the focused integration test and verify it passes**

Run: `pnpm test src/pages/Home/index.test.tsx`

Expected: PASS; the container owns hooks while `HomeShell` remains presentation-only.

- [ ] **Step 5: Commit Home composition**

```bash
git add src/pages/Home/index.tsx src/pages/Home/index.test.tsx
git commit -m "feat(home): compose user and logout state"
```

### Task 4: Unify the Authentication UI

**Files:**
- Modify: `src/components/Auth/AuthLayout.tsx`
- Modify: `src/components/Auth/LoginForm.tsx`
- Modify: `src/components/Auth/RegisterForm.tsx`
- Modify: `src/index.css`

- [ ] **Step 1: Add a failing accessible-auth-layout assertion**

Create `src/components/Auth/AuthLayout.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AuthLayout from './AuthLayout';

describe('AuthLayout', () => {
  it('provides a named authentication region around its content', () => {
    render(<AuthLayout title="登录 AIM"><form aria-label="登录表单" /></AuthLayout>);
    expect(screen.getByRole('heading', { name: '登录 AIM' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '登录 AIM' })).toContainElement(screen.getByRole('form', { name: '登录表单' }));
  });
});
```

- [ ] **Step 2: Run the focused layout test and verify it fails because the current layout has no named region**

Run: `pnpm test src/components/Auth/AuthLayout.test.tsx`

Expected: FAIL because the current rendered tree lacks a `region` role named after the title.

- [ ] **Step 3: Implement the shared authentication frame**

Replace the `AuthLayout` markup with this semantic structure while preserving its `children` and `title` props:

```tsx
export default function AuthLayout({ children, title }: AuthLayoutProps) {
  return (
    <main className="auth-page">
      <header className="auth-brand" aria-label="AIM">AIM</header>
      <section className="auth-panel" aria-label={title}>
        <p className="auth-eyebrow">AIM</p>
        <h1>{title}</h1>
        {children}
      </section>
    </main>
  );
}
```

Add `className="auth-form"` to both Ant Design `Form` elements. Keep each current field name, validation rule, autocomplete value, submit handler, and button state. Do not change request payloads or mock code.

Append these rules to `src/index.css` after the existing autofill rules:

```css
:root { font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", sans-serif; color: #1d1d1f; background: #f5f5f7; }
body { min-width: 320px; margin: 0; background: #f5f5f7; }
button, input { font: inherit; }
.auth-page { display: grid; min-height: 100dvh; place-items: center; padding: 24px; background: #f5f5f7; }
.auth-brand { position: fixed; top: 24px; left: 28px; font-size: 18px; font-weight: 700; letter-spacing: 0; }
.auth-panel { width: min(100%, 400px); padding: 32px; border: 1px solid rgba(0, 0, 0, .08); border-radius: 8px; background: #fff; box-shadow: 0 18px 50px rgba(0, 0, 0, .08); }
.auth-eyebrow { margin: 0 0 8px; color: #6e6e73; font-size: 13px; font-weight: 600; }
.auth-panel h1 { margin: 0 0 28px; font-size: 28px; font-weight: 650; line-height: 1.2; }
.auth-form .ant-form-item-label > label { color: #424245; font-size: 14px; }
.auth-form .ant-input, .auth-form .ant-input-affix-wrapper { border-color: rgba(0, 0, 0, .14); border-radius: 8px; min-height: 44px; box-shadow: none; }
.auth-form .ant-input:focus, .auth-form .ant-input-affix-wrapper-focused { border-color: #0071e3; box-shadow: 0 0 0 3px rgba(0, 113, 227, .16); }
.auth-form .ant-btn-primary { min-height: 44px; border-radius: 8px; background: #0071e3; box-shadow: none; }
.auth-form .ant-btn-primary:hover { background: #0077ed !important; }
@media (max-width: 480px) { .auth-page { padding: 16px; align-items: start; padding-top: 104px; } .auth-brand { top: 20px; left: 20px; } .auth-panel { padding: 24px; } }
```

- [ ] **Step 4: Run the focused layout test and verify it passes**

Run: `pnpm test src/components/Auth/AuthLayout.test.tsx`

Expected: PASS; the accessible region contains the supplied form.

- [ ] **Step 5: Commit authentication visual work**

```bash
git add src/components/Auth/AuthLayout.tsx src/components/Auth/LoginForm.tsx src/components/Auth/RegisterForm.tsx src/components/Auth/AuthLayout.test.tsx src/index.css
git commit -m "feat(auth): refine authentication interface"
```

### Task 5: Full Verification and Completion

**Files:**
- Modify: only files created or modified by Tasks 1-4 when verification finds a direct defect.

- [ ] **Step 1: Run the full frontend suite**

Run: `pnpm test`

Expected: all existing and newly added tests pass with zero failures.

- [ ] **Step 2: Run static validation and the production build**

Run: `pnpm lint`

Expected: exit code 0.

Run: `pnpm build`

Expected: TypeScript and Vite complete with exit code 0.

- [ ] **Step 3: Run repository-mandated backend checks from the documented Maven root**

Run from `D:\Workspace\AIM\backend`: `mvn compile`

Expected: Maven exits with code 0.

Run from `D:\Workspace\AIM\backend`: `mvn test`

Expected: Maven exits with code 0.

- [ ] **Step 4: Manually inspect the running UI at desktop and narrow widths**

Run: `pnpm dev -- --host 127.0.0.1`

Inspect `/login`, `/register`, and authenticated `/home` at 1440px and 390px widths. Confirm readable text, intact focus styles, no overlapping regions, no horizontal overflow, a stable account loading placeholder, and that logout returns to `/login`.

- [ ] **Step 5: Review the final diff, remove temporary files, and create the task commit**

Run: `git diff --check` and `git status --short`.

Stage only the production code, tests, and plan document created by this task. Do not stage the user-owned `src/hooks/useUser.ts` change. Remove any temporary inspection artifacts before committing.

```bash
git add docs/superpowers/plans/2026-07-24-auth-home-shell.md src/components/Auth src/components/Home src/hooks/useAuth.ts src/hooks/useAuth.test.tsx src/index.css src/pages/Home
git commit -m "feat(home): build initial workspace shell"
```
