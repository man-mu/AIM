import {useNavigate} from "react-router";
import {useMutation, useQueryClient} from "@tanstack/react-query";
import {authApi} from "@/apis/auth.ts";
import {storage} from "@/utils/storage.ts";
import type {LoginParams, LogoutParams, RegisterParams} from "@/types/Auth/Auth.ts";
import {useAuthStore} from "@/stores/useAuthStore.ts";

export const useLogin = ()=> {
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (params: LoginParams) => authApi.login(params),
        onSuccess: (data) => {
            const { tokens, user } = data;
            storage.setAccessToken(tokens.accessToken);
            storage.setRefreshToken(tokens.refreshToken);
            storage.setAccessExpire(tokens.accessExpire);

            queryClient.setQueryData(['user'], user);
            navigate('/home', {
                replace: true
            });
        },
        onError: (error) => {
            alert(error.message);
        }
    })
}
export const useRegister = () => {
    const navigate = useNavigate();

    return useMutation({
        mutationFn: (params: RegisterParams) => authApi.register(params),
        onSuccess: () => {
            navigate('/login');
        },
        onError: (error: Error) => {
            console.error('注册失败:', error.message);
        },
    });
};
export const useLogout = () => {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const clearAuth = useAuthStore((state) => state.clearAuth);

    return useMutation({
        mutationFn: (params: LogoutParams) => authApi.logout(params),
        onSuccess: () => {
            queryClient.clear();
            storage.clearAuth();
            clearAuth();
            navigate('/login');
        },
        onError: () => {
            queryClient.clear();
            storage.clearAuth();
            clearAuth();
            navigate('/login');
        },
    });
};