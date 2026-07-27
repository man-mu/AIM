import { QueryClient } from '@tanstack/react-query';
import { isApiError } from '@/lib/result';

/**
 * 全局 QueryClient 默认策略：
 * - 业务错误（ApiError，code!==0）不重试——重试不会改变业务结果；
 *   网络层错误最多重试 2 次；
 * - refetchOnWindowFocus：回到窗口即校准服务端状态（IM 高实时场景合理）；
 * - staleTime 30s：与实时事件配合，事件负责推、query 负责兜底拉。
 */
export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => !isApiError(error) && failureCount < 2,
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        refetchOnReconnect: 'always',
      },
      mutations: {
        retry: 0,
      },
    },
  });
}

export const queryClient = createAppQueryClient();
