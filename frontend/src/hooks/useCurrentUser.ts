import { useMemo } from 'react';
import { useAuthStore } from '@/stores/useAuthStore';
import { mapUser, type UiUser } from '@/modules/user/hooks';

/** 归一化后的当前用户（id 为 string）；未登录为 null。 */
export function useCurrentUser(): UiUser | null {
  const user = useAuthStore((state) => state.user);
  return useMemo(() => (user ? mapUser(user) : null), [user]);
}
