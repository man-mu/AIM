import { systemScheduler, type Scheduler } from '@/lib/clock';
import { err, type ApiEnvelope } from '@/lib/result';
import { MockDb, MockDbError, type DbSnapshot } from './db';
import { createMockRouter, splitUrl, type MockRouter } from './engine/router';
import { parseBearer } from './engine/tokens';
import { normalizeOutcome, type HttpMethod, type MockOutcome, type MockRequest } from './engine/types';
import { createAuthRoutes } from './handlers/auth';
import { createConvRoutes } from './handlers/conv';
import { nullEventSink, type HandlerContext, type MockEventSink } from './handlers/context';
import { createFileRoutes } from './handlers/file';
import { createFriendRoutes } from './handlers/friend';
import { createMessageRoutes } from './handlers/message';
import { createNotificationRoutes } from './handlers/notification';
import { createUserRoutes } from './handlers/user';

/**
 * Mock 平台核心（传输无关）：
 * 组装 db + 路由表 + 鉴权 + 事件出口，暴露 handle() 给任意传输层
 * （浏览器里是 axios adapter；测试里直接调用）。
 */
export interface MockPlatformOptions {
  snapshot?: DbSnapshot | null;
  scheduler?: Scheduler;
  events?: MockEventSink;
  onMutated?(db: MockDb): void;
  afterUserMessage?: HandlerContext['afterUserMessage'];
  /** 快速演示账号（admin/admin123、test/test123）。 */
  seedDemoAccounts?: boolean;
}

export interface HandleInput {
  method: string;
  /** 相对 url（可带 query），如 `/convs/1?pageNum=1`。 */
  url: string;
  params?: Record<string, unknown>;
  body?: unknown;
  authorization?: string | null;
}

export interface MockPlatform {
  db: MockDb;
  router: MockRouter;
  handle(input: HandleInput): MockOutcome | null;
}

const MUTATING_METHODS = new Set(['POST', 'PUT', 'DELETE', 'PATCH']);

export function createMockPlatform(options: MockPlatformOptions = {}): MockPlatform {
  const scheduler = options.scheduler ?? systemScheduler;
  const db = new MockDb(options.snapshot ?? null);
  db.seedNpcs(scheduler.now());

  if (options.seedDemoAccounts ?? true) {
    for (const demo of [
      { username: 'admin', password: 'admin123', phone: '13800138000' },
      { username: 'test', password: 'test123', phone: '13800138001' },
    ]) {
      if (!db.users.findByAccount(demo.username)) {
        db.users.create({ ...demo, email: `${demo.username}@aim.local` }, scheduler.now());
      }
    }
  }

  const ctx: HandlerContext = {
    db,
    now: () => scheduler.now(),
    events: options.events ?? nullEventSink,
    afterUserMessage: options.afterUserMessage,
  };

  const router = createMockRouter([
    ...createAuthRoutes(ctx),
    ...createUserRoutes(ctx),
    ...createConvRoutes(ctx),
    ...createMessageRoutes(ctx),
    ...createFileRoutes(ctx),
    ...createFriendRoutes(ctx),
    ...createNotificationRoutes(ctx),
  ]);

  const handle = (input: HandleInput): MockOutcome | null => {
    const { path, query } = splitUrl(input.url);
    const match = router.resolve(input.method, path);
    if (!match) {
      return null;
    }

    // GET 的 params 合入 query（axios 将 params 序列化进最终 URL，这里等价处理）。
    if (input.params) {
      for (const [key, value] of Object.entries(input.params)) {
        if (value !== undefined && value !== null) {
          query[key] = String(value);
        }
      }
    }

    let userId: string | null = null;
    if (!match.isPublic) {
      const payload = parseBearer(input.authorization);
      if (!payload || payload.kind !== 'access' || payload.expireAt <= scheduler.now()) {
        return { status: 401, envelope: err(401, 'unauthorized') };
      }
      userId = payload.userId;
    }

    const request: MockRequest = {
      method: input.method.toUpperCase() as HttpMethod,
      path,
      params: match.params,
      query,
      body: typeof input.body === 'string' ? safeParse(input.body) : input.body,
      userId,
    };

    let outcome: MockOutcome;
    try {
      outcome = normalizeOutcome(match.handler(request));
    } catch (error) {
      if (error instanceof MockDbError) {
        outcome = { status: 200, envelope: err(error.code, error.message) };
      } else {
        console.error('[mock] handler crashed', input.method, path, error);
        outcome = { status: 200, envelope: err(500, 'internal error') };
      }
    }

    if (MUTATING_METHODS.has(request.method) && outcome.envelope.code === 0) {
      options.onMutated?.(db);
    }
    return outcome;
  };

  return { db, router, handle };
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

export type { MockOutcome, ApiEnvelope };
