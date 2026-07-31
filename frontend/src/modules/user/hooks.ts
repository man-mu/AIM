import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/apis/queryKeys';
import { userApi } from '@/apis/user';
import { toast } from '@/components/ui/toast/toastBus';
import { toInt64String } from '@/lib/ids';
import { useAuthStore } from '@/stores/useAuthStore';
import type { UpdateParams, UpdatePasswordParams, UserInfo } from '@/types/User/User';

export interface UiUser {
  id: string;
  username: string;
  avatar: string;
  bio: string;
  phone: string;
  email: string;
  gender: 0 | 1 | 2;
  birthday: number;
}

export function mapUser(dto: UserInfo): UiUser {
  return {
    id: toInt64String(dto.id),
    username: dto.username,
    avatar: dto.avatar,
    bio: dto.bio,
    phone: dto.phone,
    email: dto.email,
    gender: dto.gender,
    birthday: dto.birthday,
  };
}

/** 资料更新：成功后同步 Query 缓存与 authStore（侧栏头像即时刷新）。 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: (patch: UpdateParams) => userApi.updateProfile(patch),
    onSuccess: (user) => {
      queryClient.setQueryData(['user'], user);
      setAuth(user);
      toast.success('资料已更新');
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : '资料更新失败');
    },
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (params: UpdatePasswordParams) => userApi.updatePassword(params),
    onSuccess: () => {
      toast.success('密码已修改');
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : '密码修改失败');
    },
  });
}

/** 用户搜索（发起会话 / 添加好友共用）。 */
export function useUserSearch(keyword: string) {
  const normalized = keyword.trim();
  return useQuery<UiUser[]>({
    queryKey: queryKeys.users.search(normalized, 1),
    queryFn: async ({ signal }) => {
      const data = await userApi.searchUsers({ keyword: normalized, pageNum: 1, pageSize: 20 }, { signal });
      return data.users.map(mapUser);
    },
    enabled: normalized.length > 0,
    staleTime: 30_000,
  });
}
