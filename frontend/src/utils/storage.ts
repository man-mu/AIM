const ACCESS_TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
const ACCESS_EXPIRE_KEY = 'access_expire';

export const storage = {
    // 访问令牌
    getAccessToken: () => localStorage.getItem(ACCESS_TOKEN_KEY),
    setAccessToken: (token: string) => localStorage.setItem(ACCESS_TOKEN_KEY, token),
    removeAccessToken: () => localStorage.removeItem(ACCESS_TOKEN_KEY),
    // 刷新令牌
    getRefreshToken: () => localStorage.getItem(REFRESH_TOKEN_KEY),
    setRefreshToken: (token: string) => localStorage.setItem(REFRESH_TOKEN_KEY, token),
    removeRefreshToken: () => localStorage.removeItem(REFRESH_TOKEN_KEY),
    // 访问令牌过期时间
    getAccessExpire: () => Number(localStorage.getItem(ACCESS_EXPIRE_KEY)),
    setAccessExpire: (time: number) => localStorage.setItem(ACCESS_EXPIRE_KEY, String(time)),
    removeAccessExpire: () => localStorage.removeItem(ACCESS_EXPIRE_KEY),
    // 快捷清空所有认证信息
    clearAuth: () => {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(ACCESS_EXPIRE_KEY);
    },
}