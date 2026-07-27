import type { Method } from 'axios';
import client from './client.ts';
import { unwrapEnvelope, type ApiEnvelope } from '@/lib/result';

export interface RequestOptions {
  signal?: AbortSignal;
  /** 少数接口需要（如登出）：跳过登录态失效处理。 */
  skipAuthHandling?: boolean;
}

/**
 * 统一请求入口：自动解包 Result<T>。
 * - code !== 0 时抛出 ApiError（message 已映射为用户可读文案）；
 * - GET 的 submitData 作为 query 参数，其余作为 JSON body；
 * - 透传 AbortSignal，配合 TanStack Query 在路由切换时取消请求。
 */
export const request = <T>(
  url: string,
  method: Method = 'GET',
  submitData?: object,
  options?: RequestOptions,
): Promise<T> => {
  return client
    .request<ApiEnvelope<T>>({
      url,
      method,
      ...(method.toUpperCase() === 'GET' ? { params: submitData } : { data: submitData }),
      signal: options?.signal,
      skipAuthHandling: options?.skipAuthHandling,
    })
    .then((res) => unwrapEnvelope<T>(res.data, url));
};
