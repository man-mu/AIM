import type { ApiEnvelope } from '@/lib/result';

/**
 * Mock 引擎协议：与传输层（axios adapter）完全解耦的纯 TS 类型。
 * handlers 只认识 MockRequest / MockOutcome，方便在 Node 环境直接单测。
 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface MockRequest {
  method: HttpMethod;
  /** 不含 query 的路径，如 `/convs/123/members`。 */
  path: string;
  /** 路径参数（:conversationId 等占位捕获）。 */
  params: Record<string, string>;
  /** query 参数（GET 的 submitData 与 url 中 ? 后内容合并）。 */
  query: Record<string, string>;
  body: unknown;
  /** 已鉴权用户 ID（public 路由为 null）。 */
  userId: string | null;
}

export interface MockOutcome {
  /** HTTP 状态码，默认 200；401 用于触发前端刷新流程。 */
  status: number;
  envelope: ApiEnvelope<unknown>;
}

export type MockHandler = (request: MockRequest) => MockOutcome | ApiEnvelope<unknown>;

export interface RouteSpec {
  method: HttpMethod;
  pattern: string;
  handler: MockHandler;
  /** 白名单路由：不要求 Authorization。 */
  isPublic?: boolean;
}

export function outcome(envelope: ApiEnvelope<unknown>, status = 200): MockOutcome {
  return { status, envelope };
}

export function normalizeOutcome(value: MockOutcome | ApiEnvelope<unknown>): MockOutcome {
  if ('envelope' in value && typeof value.status === 'number') {
    return value;
  }
  return { status: 200, envelope: value as ApiEnvelope<unknown> };
}
