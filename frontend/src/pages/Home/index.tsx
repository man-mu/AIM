import { useRef, useState } from 'react';
import HomeShell from '@/components/Home/HomeShell';
import { useLocalLogout } from '@/hooks/useAuth';
import { useUser } from '@/hooks/useUser';
import { useAuthStore } from '@/stores/useAuthStore';

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
    <HomeShell
      user={userQuery.data ?? cachedUser}
      isUserLoading={userQuery.isLoading && !cachedUser}
      isLoggingOut={isLoggingOut || localLogout.isPending}
      onLogout={handleLogout}
    />
  );
}
