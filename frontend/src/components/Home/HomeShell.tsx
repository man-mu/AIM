import {
  BellOutlined,
  LogoutOutlined,
  MessageOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import type { UserInfo } from '@/types/User/User';

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

const navigationItems = [
  { label: '\u6d88\u606f', icon: MessageOutlined, active: true },
  { label: '\u8054\u7cfb\u4eba', icon: TeamOutlined, active: false },
  { label: '\u901a\u77e5', icon: BellOutlined, active: false },
];

function accountInitial(user: UserInfo | null): string {
  return user?.username?.trim().slice(0, 1).toUpperCase() || 'A';
}

export default function HomeShell({
  user,
  isUserLoading,
  isLoggingOut,
  isMobileChatOpen,
  sidebarContent,
  chatContent,
  detailContent,
  onLogout,
}: HomeShellProps) {
  return (
    <main className="min-h-screen bg-[#f5f5f7] p-3 text-[#1d1d1f] sm:p-5">
      <section className="mx-auto grid min-h-[calc(100vh-1.5rem)] max-w-[1440px] overflow-hidden border border-black/[0.08] bg-white shadow-[0_18px_50px_rgba(0,0,0,0.08)] sm:min-h-[calc(100vh-2.5rem)] sm:grid-cols-[248px_minmax(0,1fr)] lg:grid-cols-[248px_minmax(0,1fr)_280px]">
        <aside
          data-testid="home-sidebar"
          className={
            isMobileChatOpen
              ? 'hidden min-h-[244px] flex-col border-b border-black/[0.08] bg-[#fbfbfd] p-4 sm:flex sm:min-h-0 sm:border-r sm:border-b-0'
              : 'flex min-h-[244px] flex-col border-b border-black/[0.08] bg-[#fbfbfd] p-4 sm:min-h-0 sm:border-r sm:border-b-0'
          }
        >
          <div className="px-2 text-lg font-semibold">AIM</div>
          <nav className="mt-8" aria-label={'\u4e3b\u5bfc\u822a'}>
            <ul className="grid gap-1">
              {navigationItems.map(({ label, icon: Icon, active }) => (
                <li
                  key={label}
                  className={
                    active
                      ? 'flex items-center gap-3 rounded-lg bg-black/[0.06] px-3 py-2.5 text-sm font-medium'
                      : 'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-[#6e6e73]'
                  }
                >
                  <Icon aria-hidden />
                  <span>{label}</span>
                </li>
              ))}
            </ul>
          </nav>
          <div className="mt-6 min-h-0 flex-1 overflow-y-auto">{sidebarContent}</div>
          <div className="mt-6 border-t border-black/[0.08] pt-4">
            {isUserLoading ? (
              <div
                aria-label={'\u6b63\u5728\u52a0\u8f7d\u8d26\u6237\u8d44\u6599'}
                className="h-10 animate-pulse rounded-lg bg-black/[0.06]"
                data-testid="account-skeleton"
              />
            ) : (
              <div className="flex items-center gap-3 px-1">
                <span className="grid size-9 place-items-center rounded-full bg-[#0071e3] text-sm font-semibold text-white">
                  {accountInitial(user)}
                </span>
                <span className="min-w-0 truncate text-sm font-medium">{user?.username || '\u5f53\u524d\u8d26\u6237'}</span>
              </div>
            )}
            <button
              type="button"
              onClick={onLogout}
              disabled={isLoggingOut}
              className="mt-3 inline-flex items-center gap-2 rounded-lg border border-black/[0.12] px-3 py-2 text-sm font-medium text-[#424245] transition hover:border-black/[0.25] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#0071e3] disabled:cursor-not-allowed disabled:opacity-50"
            >
              <LogoutOutlined aria-hidden />
              {'\u9000\u51fa\u767b\u5f55'}
            </button>
          </div>
        </aside>

        <section
          data-testid="home-chat-panel"
          className={
            isMobileChatOpen
              ? 'flex min-h-[420px] flex-col sm:min-h-0'
              : 'hidden min-h-[420px] flex-col sm:flex sm:min-h-0'
          }
        >
          {chatContent}
        </section>

        <aside data-testid="home-detail-panel" className="hidden border-l border-black/[0.08] bg-[#fbfbfd] p-6 lg:block">
          {detailContent}
        </aside>
      </section>
    </main>
  );
}
