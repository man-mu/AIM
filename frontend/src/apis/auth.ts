import { request } from './request.ts';
import type {
  LoginData,
  LoginParams,
  LogoutParams,
  RefreshData,
  RefreshParams,
  RegisterData,
  RegisterParams,
  ValidateData,
} from '@/types/Auth/Auth.ts';

export const authApi = {
  register: (data: RegisterParams) => {
    return request<RegisterData>('/auth/register', 'POST', data);
  },
  login: (data: LoginParams) => {
    return request<LoginData>('/auth/login', 'POST', data);
  },
  /** 登出：即使失败也应清理本地会话，调用侧负责兜底。 */
  logout: (data: LogoutParams) => {
    return request('/auth/logout', 'POST', data, { skipAuthHandling: true });
  },
  validate: () => {
    return request<ValidateData>('/auth/validate', 'GET');
  },
  refresh: (data: RefreshParams) => {
    return request<RefreshData>('/auth/refresh', 'POST', data);
  },
};
