import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { BASE_URL } from '@/constant';
import { mockHandlers, shouldMock } from '@/mocks';
import router from '@/router';
import { useAuthStore } from '@/stores/useAuthStore';
import { parseJsonResponse } from '@/utils/json';
import { storage } from '@/utils/storage';

const PUBLIC_AUTH_PATHS = ['/auth/login', '/auth/register'];
const WHITE_LIST = [...PUBLIC_AUTH_PATHS, '/public/'];
const AUTH_FAILURE_CODES = new Set([401, 10005, 10006]);

function isPublicAuthPath(url?: string): boolean {
  return PUBLIC_AUTH_PATHS.some((path) => url?.includes(path));
}

function clearSessionAndRedirect(): void {
  storage.clearAuth();
  useAuthStore.getState().clearAuth();
  void router.navigate('/login', { replace: true });
}

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  transformResponse: [parseJsonResponse],
});

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (!shouldMock()) return config;

  const url = config.url || '';
  const handler = mockHandlers[url];
  if (handler) {
    const body = config.data ? (typeof config.data === 'string' ? JSON.parse(config.data) : config.data) : {};
    const mockData = handler(body);
    config.adapter = () => Promise.resolve({
      data: mockData,
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    });
  }
  return config;
});

client.interceptors.request.use(
  (config) => {
    const isWhiteListed = WHITE_LIST.some((path) => config.url?.includes(path));
    if (!isWhiteListed) {
      const token = storage.getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error),
);

client.interceptors.response.use(
  (response) => {
    const { code } = response.data ?? {};
    if (!isPublicAuthPath(response.config.url) && AUTH_FAILURE_CODES.has(code)) {
      clearSessionAndRedirect();
    }
    return response;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401 && !isPublicAuthPath(error.config?.url)) {
      clearSessionAndRedirect();
    }
    return Promise.reject(error);
  },
);

export default client;
