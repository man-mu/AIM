import { messageForCode } from './errorCodes';

/**
 * 后端统一响应外壳 `Result<T>`：{ code, message, data }。
 * code === 0 成功；非 0 业务失败（data 为 null）。
 */
export interface ApiEnvelope<T = unknown> {
  code: number;
  message: string;
  data: T | null;
}

/**
 * 业务错误：保留原始 code 与服务端 message，
 * `message` 本身已是可直接展示给用户的中文文案。
 */
export class ApiError extends Error {
  readonly code: number;
  readonly serverMessage: string;
  readonly requestUrl?: string;

  constructor(code: number, serverMessage: string, requestUrl?: string) {
    super(messageForCode(code, serverMessage));
    this.name = 'ApiError';
    this.code = code;
    this.serverMessage = serverMessage;
    this.requestUrl = requestUrl;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isEnvelope(value: unknown): value is ApiEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { code?: unknown }).code === 'number' &&
    typeof (value as { message?: unknown }).message === 'string'
  );
}

/**
 * 解包 `Result<T>`：成功返回 data（无数据接口返回 undefined），失败抛 ApiError。
 */
export function unwrapEnvelope<T>(envelope: ApiEnvelope<T>, requestUrl?: string): T {
  if (envelope.code !== 0) {
    throw new ApiError(envelope.code, envelope.message, requestUrl);
  }
  return (envelope.data ?? undefined) as T;
}

/** Mock/测试侧构造响应的便捷函数。 */
export function ok<T>(data: T, message = 'success'): ApiEnvelope<T> {
  return { code: 0, message, data };
}

export function err(code: number, message: string): ApiEnvelope<never> {
  return { code, message, data: null };
}
