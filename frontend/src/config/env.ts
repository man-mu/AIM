/**
 * 环境变量唯一入口。
 *
 * 全项目只有本文件允许触碰 import.meta.env —— 其他模块一律消费 appEnv，
 * 保证核心逻辑可脱离 Vite 环境运行（Node 测试 / Worker / SSR 均安全）。
 */
export interface AppEnv {
  /** REST 网关地址（gateway-service，默认 9080）。 */
  apiBaseUrl: string;
  /** WebSocket 网关地址（ws-gateway-service，Phase B 使用）。 */
  wsUrl: string;
  /** 是否启用前端 Mock 后端（无后端开发模式）。 */
  useMock: boolean;
  isDev: boolean;
}

function readString(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function readEnv(): AppEnv {
  const raw = import.meta.env;
  return {
    apiBaseUrl: readString(raw.VITE_API_BASE_URL, 'http://localhost:9080/api/v1'),
    wsUrl: readString(raw.VITE_WS_URL, 'ws://localhost:8081/ws'),
    useMock: raw.VITE_USE_MOCK === 'true',
    isDev: raw.DEV === true,
  };
}

export const appEnv: AppEnv = readEnv();

/** @deprecated 兼容旧引用，等价 appEnv.apiBaseUrl。 */
export const API_BASE_URL = appEnv.apiBaseUrl;
