import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { authApi } from '@/apis/auth.ts';
import { useAuthStore } from '@/stores/useAuthStore.ts';
import type { LoginParams, LogoutParams, RegisterParams } from '@/types/Auth/Auth.ts';
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
      alert(getErrorMessage(error, '登录失败，请稍后重试'));
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
      alert(getErrorMessage(error, '注册失败，请稍后重试'));
    },
  });
};

export const useLogout = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((state) => state.clearAuth);

  const clearLocalSession = () => {
    queryClient.clear();
    storage.clearAuth();
    clearAuth();
    navigate('/login', { replace: true });
  };

  return useMutation({
    mutationFn: (params: LogoutParams) => authApi.logout(params),
    onSuccess: clearLocalSession,
    onError: clearLocalSession,
  });
};
