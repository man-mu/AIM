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
