import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { authApi } from '@/apis/auth.ts';
import { toast } from '@/components/ui/toast/toastBus';
import { useAuthStore } from '@/stores/useAuthStore.ts';
import type { LoginParams, RegisterParams } from '@/types/Auth/Auth.ts';
import { establishAuthSession } from '@/utils/authSession';
import { storage } from '@/utils/storage.ts';

function getErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

export const useLogin = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: (params: LoginParams) => authApi.login(params),
    onSuccess: (data) => {
      establishAuthSession(data, setAuth);
      queryClient.setQueryData(['user'], data.user);
      navigate('/home', { replace: true });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, '登录失败，请稍后重试'));
    },
  });
};

export const useRegister = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setAuth = useAuthStore((state) => state.setAuth);

  return useMutation({
    mutationFn: (params: RegisterParams) => authApi.register(params),
    onSuccess: (data) => {
      establishAuthSession(data, setAuth);
      queryClient.setQueryData(['user'], data.user);
      navigate('/home', { replace: true });
    },
    onError: (error) => {
      toast.error(getErrorMessage(error, '注册失败，请稍后重试'));
    },
  });
};

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

/** 仅清理本地会话（不调服务端），异常兜底场景使用。 */
export const useLocalLogout = () => {
  const clearLocalSession = useClearLocalSession();

  return useMutation({
    mutationFn: async () => undefined,
    onSuccess: () => {
      clearLocalSession();
      toast.success('已退出登录');
    },
  });
};

/**
 * 标准登出：吊销服务端 refreshToken，无论成败都清理本地会话。
 */
export const useLogout = () => {
  const clearLocalSession = useClearLocalSession();

  return useMutation({
    mutationFn: async () => {
      const refreshToken = storage.getRefreshToken();
      if (refreshToken) {
        await authApi.logout({ refreshToken });
      }
    },
    onSuccess: () => {
      clearLocalSession();
      toast.success('已退出登录');
    },
    onError: () => {
      // 服务端吊销失败不阻塞本地登出。
      clearLocalSession();
      toast.success('已退出登录');
    },
  });
};
