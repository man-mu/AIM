import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { appBus } from '@/app/appBus';
import { appEnv } from '@/config/env';
import { isAuthFailureCode } from '@/lib/errorCodes';
import { ApiError, isEnvelope, unwrapEnvelope, type ApiEnvelope } from '@/lib/result';
import { createSingleFlight } from '@/lib/singleFlight';
import { installMockAdapter } from '@/mocks';
import { useAuthStore } from '@/stores/useAuthStore';
import { parseJsonResponse } from '@/utils/json';
import { storage } from '@/utils/storage';

/**
 * HTTP 客户端：token 注入 + 静默刷新 + 失效登出，全部收敛在本文件。
 *
 * 登录态失效处理策略（proactive + reactive 双保险）：
 *  1. proactive —— 请求前发现 accessToken 即将过期（<30s），先单飞刷新再发；
 *  2. reactive  —— 响应命中 401 / 10005 / 10006 时单飞刷新并重放原请求（至多一次）；
 *  3. 刷新不可用或失败 —— 清空会话，emit `auth:expired`（由 UI 层跳转登录页）。
 */
const PUBLIC_AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

/** accessToken 剩余有效期低于该值时，主动刷新。 */
const PROACTIVE_REFRESH_SKEW_MS = 30_000;

function isPublicAuthPath(url?: string): boolean {
  return PUBLIC_AUTH_PATHS.some((path) => url?.includes(path));
}

const client = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: 15_000,
  transformResponse: [parseJsonResponse],
});

// Mock 平台以 axios adapter 形式挂载；未开启 mock 时是纯 no-op。
installMockAdapter(client);

// ---------------------------------------------------------------------------
// 静默刷新（单飞）
// ---------------------------------------------------------------------------
interface RefreshPayload {
  accessToken: string;
  accessExpire: number;
}

const runTokenRefresh = createSingleFlight(async (): Promise<void> => {
  const refreshToken = storage.getRefreshToken();
  if (!refreshToken || storage.getRefreshExpire() <= Date.now()) {
    throw new ApiError(10006, 'refresh token 不可用');
  }

  const response = await client.request<ApiEnvelope<RefreshPayload>>({
    url: '/auth/refresh',
    method: 'POST',
    data: { refreshToken },
    skipAuthHandling: true,
  });
  const data = unwrapEnvelope(response.data, '/auth/refresh');
  storage.setAccessToken(data.accessToken);
  storage.setAccessExpire(data.accessExpire);
});

let sessionExpiredNotified = false;

function hardExpireSession(reason: 'token-expired' | 'refresh-failed'): void {
  storage.clearAuth();
  useAuthStore.getState().clearAuth();
  if (!sessionExpiredNotified) {
    sessionExpiredNotified = true;
    // 短暂防抖：并发失败只通知一次，随后允许下一轮登录后再次触发。
    setTimeout(() => {
      sessionExpiredNotified = false;
    }, 1_000);
    appBus.emit('auth:expired', { reason });
  }
}

function shouldProactivelyRefresh(): boolean {
  const accessToken = storage.getAccessToken();
  const refreshToken = storage.getRefreshToken();
  if (!accessToken || !refreshToken) {
    return false;
  }
  const accessExpire = storage.getAccessExpire();
  const refreshExpire = storage.getRefreshExpire();
  return (
    Number.isFinite(accessExpire) &&
    accessExpire - Date.now() < PROACTIVE_REFRESH_SKEW_MS &&
    refreshExpire > Date.now()
  );
}

async function replayWithFreshToken<T>(
  config: InternalAxiosRequestConfig,
  fallback: () => T | Promise<T>,
): Promise<T | AxiosResponse> {
  try {
    await runTokenRefresh();
  } catch {
    hardExpireSession('refresh-failed');
    return fallback();
  }
  return client.request({ ...config, authRetried: true });
}

// ---------------------------------------------------------------------------
// 拦截器
// ---------------------------------------------------------------------------
client.interceptors.request.use(async (config) => {
  if (isPublicAuthPath(config.url) || config.skipAuthHandling) {
    return config;
  }

  if (shouldProactivelyRefresh()) {
    // 主动刷新失败不阻塞请求：让其自然 401 后走 reactive 流程兜底。
    await runTokenRefresh().catch(() => undefined);
  }

  const token = storage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => {
    const config = response.config;
    if (config.skipAuthHandling || config.authRetried || isPublicAuthPath(config.url)) {
      return response;
    }

    const body: unknown = response.data;
    if (isEnvelope(body) && isAuthFailureCode(body.code)) {
      // 刷新失败时返回原响应：由 request.ts 解包并抛出对应 ApiError。
      return replayWithFreshToken(config, () => response);
    }
    return response;
  },
  (error: unknown) => {
    const axiosError = error as { response?: { status?: number }; config?: InternalAxiosRequestConfig };
    const config = axiosError.config;
    if (
      axiosError.response?.status === 401 &&
      config &&
      !config.skipAuthHandling &&
      !config.authRetried &&
      !isPublicAuthPath(config.url)
    ) {
      return replayWithFreshToken(config, () => Promise.reject(error));
    }
    return Promise.reject(error);
  },
);

export default client;
