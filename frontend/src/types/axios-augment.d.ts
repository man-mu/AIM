import 'axios';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 该请求已经历过一次“刷新后重放”，避免无限循环。 */
    authRetried?: boolean;
    /** 跳过登录态失效处理（refresh 自身、登出等）。 */
    skipAuthHandling?: boolean;
  }
}
