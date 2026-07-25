import { useQuery } from '@tanstack/react-query';
import { userApi } from '@/apis/user';
import { storage } from '@/utils/storage';
import { useAuthStore } from '@/stores/useAuthStore.ts';
import type { UserInfo } from '@/types/User/User.ts';

export const useUser = () => {
    const setAuth = useAuthStore((state) => state.setAuth);

    return useQuery<UserInfo>({
        queryKey: ['user'],
        queryFn: async () => {
            const data = await userApi.getProfile();
            // 请求成功后，顺便把用户信息同步到 Zustand，方便 UI 即时响应
            setAuth(data);
            return data;
        },
        // 只有当 accessToken 存在时才请求
        enabled: !!storage.getAccessToken(),
        // 缓存 5 分钟，减少不必要的请求
        staleTime: 5 * 60 * 1000,
        // 如果 401 或 code !== 0，不重试，直接跳登录
        retry: false,
    });
};