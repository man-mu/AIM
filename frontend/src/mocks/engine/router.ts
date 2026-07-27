import type { HttpMethod, MockHandler, RouteSpec } from './types';

/**
 * 极简路由匹配器：支持 `/convs/:conversationId/members/:userId/mute` 形态。
 * 匹配规则：方法一致 + 段数一致 + 静态段全等 + `:x` 段捕获为参数。
 */
export interface RouteMatch {
  handler: MockHandler;
  params: Record<string, string>;
  isPublic: boolean;
}

export interface MockRouter {
  resolve(method: string, path: string): RouteMatch | null;
  readonly routes: readonly RouteSpec[];
}

interface CompiledRoute extends RouteSpec {
  segments: string[];
}

/**
 * 定义形如 `'POST /auth/login'` 的路由表。
 */
export function createMockRouter(
  definitions: Array<[spec: string, handler: MockHandler, options?: { isPublic?: boolean }]>,
): MockRouter {
  const compiled: CompiledRoute[] = definitions.map(([spec, handler, options]) => {
    const [method, pattern] = spec.split(' ') as [HttpMethod, string];
    if (!method || !pattern || !pattern.startsWith('/')) {
      throw new Error(`invalid mock route spec: "${spec}"`);
    }
    return {
      method,
      pattern,
      handler,
      isPublic: options?.isPublic ?? false,
      segments: pattern.split('/').filter(Boolean),
    };
  });

  return {
    routes: compiled,
    resolve(method, path) {
      const normalizedMethod = method.toUpperCase();
      const pathSegments = path.split('/').filter(Boolean);

      for (const route of compiled) {
        if (route.method !== normalizedMethod || route.segments.length !== pathSegments.length) {
          continue;
        }

        const params: Record<string, string> = {};
        let matched = true;
        for (let i = 0; i < route.segments.length; i += 1) {
          const routeSegment = route.segments[i] as string;
          const pathSegment = pathSegments[i] as string;
          if (routeSegment.startsWith(':')) {
            params[routeSegment.slice(1)] = decodeURIComponent(pathSegment);
          } else if (routeSegment !== pathSegment) {
            matched = false;
            break;
          }
        }

        if (matched) {
          return { handler: route.handler, params, isPublic: route.isPublic ?? false };
        }
      }
      return null;
    },
  };
}

/** 从 axios 的相对 url 中拆出 path 与 query。 */
export function splitUrl(url: string): { path: string; query: Record<string, string> } {
  const [path = '', queryString = ''] = url.split('?');
  const query: Record<string, string> = {};
  if (queryString) {
    for (const [key, value] of new URLSearchParams(queryString)) {
      query[key] = value;
    }
  }
  return { path, query };
}
