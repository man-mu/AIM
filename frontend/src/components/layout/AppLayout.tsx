import { useEffect, useState } from 'react';
import { Outlet } from 'react-router';
import { useLogout } from '@/hooks/useAuth';
import { useUser } from '@/hooks/useUser';
import { ProfileDialog } from '@/modules/user/ProfileDialog';
import { useRealtime } from '@/realtime/useRealtime';
import { tabSync } from '@/utils/tabSync';
import { AppRail } from './AppRail';

/**
 * 登录态应用框架：
 *  - 左侧导航 + 业务页 Outlet；
 *  - 实时通道生命周期（useRealtime）挂载于此；
 *  - 多标签页登出同步（BroadcastChannel）。
 */
export function AppLayout(): React.JSX.Element {
  useUser(); // 保证刷新后拉取用户资料填充 authStore。
  useRealtime();
  const logout = useLogout();
  const [profileOpen, setProfileOpen] = useState(false);

  useEffect(() => {
    return tabSync.subscribe((message) => {
      if (message.type === 'logout') {
        // 其他标签页已登出：本页跟随（storage 已被清，直接刷新到登录页）。
        window.location.replace('/login');
      }
    });
  }, []);

  const handleLogout = (): void => {
    logout.mutate(undefined, {
      onSettled: () => tabSync.post({ type: 'logout' }),
    });
  };

  return (
    <div className="flex h-dvh min-w-80 overflow-hidden bg-[#f5f5f7] text-[#1d1d1f]">
      <AppRail onLogout={handleLogout} onOpenProfile={() => setProfileOpen(true)} />
      <main className="min-w-0 flex-1">
        <Outlet />
      </main>
      <ProfileDialog open={profileOpen} onClose={() => setProfileOpen(false)} />
    </div>
  );
}
