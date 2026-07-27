import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { appEnv } from '@/config/env';
import { systemScheduler } from '@/lib/clock';
import { createJsonKV } from '@/lib/storageKV';
import type { RealtimeFrame } from '@/realtime/protocol';
import type { DbSnapshot } from './db';
import { DB_SCHEMA_VERSION } from './db/schema';
import { parseToken } from './engine/tokens';
import type { MockEventSink } from './handlers/context';
import { createMockPlatform, type MockPlatform } from './platform';
import { createMockRealtimeHub, type MockRealtimeHub } from './realtimeHub';

/**
 * Mock 运行时装配（唯一允许触碰浏览器环境的 mock 文件）：
 *  - localStorage 快照持久化（防抖 300ms）
 *  - 从 storage 的 accessToken 推导“当前用户”
 *  - 把 MockPlatform 挂到 axios adapter；未开启 mock 时全部为 no-op
 *
 * 模拟网络延迟：60~180ms，让加载态/乐观更新路径真实可感。
 */
interface MockRuntime {
  platform: MockPlatform;
  hub: MockRealtimeHub;
}

const kv = createJsonKV('aim-mock', DB_SCHEMA_VERSION);
let runtime: MockRuntime | null = null;
let persistScheduled = false;
let stopAmbient: (() => void) | null = null;

function currentUserIdFromStorage(): string | null {
  try {
    const token = localStorage.getItem('access_token');
    const payload = parseToken(token);
    return payload && payload.expireAt > Date.now() ? payload.userId : null;
  } catch {
    return null;
  }
}

function schedulePersist(platform: MockPlatform): void {
  if (persistScheduled) {
    return;
  }
  persistScheduled = true;
  setTimeout(() => {
    persistScheduled = false;
    kv.write('db', platform.db.serialize());
  }, 300);
}

export function getMockRuntime(): MockRuntime {
  if (runtime) {
    return runtime;
  }

  const snapshot = kv.read<DbSnapshot | null>('db', null);

  // platform 先创建；hub 依赖 platform.db，事件出口通过转发 sink 延迟绑定。
  let hub: MockRealtimeHub | null = null;
  const forwardingSink: MockEventSink = { push: (event, options) => hub?.push(event, options) };

  const platform = createMockPlatform({
    snapshot,
    scheduler: systemScheduler,
    events: forwardingSink,
    afterUserMessage: (message) => hub?.notifyUserMessage(message),
    onMutated: () => schedulePersist(platform),
  });

  hub = createMockRealtimeHub({
    db: platform.db,
    scheduler: systemScheduler,
    currentUserId: currentUserIdFromStorage,
    onMutated: () => schedulePersist(platform),
  });

  runtime = { platform, hub };
  return runtime;
}

/** 供 realtime mockChannel 订阅下行事件（首次订阅时启动环境活动）。 */
export function subscribeMockRealtime(listener: (frame: RealtimeFrame) => void): () => void {
  if (!appEnv.useMock) {
    return () => undefined;
  }
  const { hub } = getMockRuntime();
  if (!stopAmbient) {
    stopAmbient = hub.startAmbient();
  }
  return hub.subscribe(listener);
}

/** 网络延迟模拟。 */
function latencyMs(): number {
  return 60 + Math.round(Math.random() * 120);
}

function headerValue(config: InternalAxiosRequestConfig, name: string): string | null {
  const headers = config.headers as unknown as Record<string, unknown> | undefined;
  const value = headers?.[name];
  return typeof value === 'string' ? value : null;
}

/**
 * 把 mock 平台安装为 axios adapter。
 * 命中路由 → 本地处理；未命中 → 走真实网络（便于灰度接入真后端）。
 */
export function installMockAdapter(instance: AxiosInstance): void {
  if (!appEnv.useMock) {
    return;
  }

  instance.interceptors.request.use((config) => {
    const { platform } = getMockRuntime();
    const outcome = platform.handle({
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      params: (config.params as Record<string, unknown> | undefined) ?? undefined,
      body: config.data,
      authorization: headerValue(config, 'Authorization'),
    });

    if (!outcome) {
      return config;
    }

    config.adapter = () =>
      new Promise<AxiosResponse>((resolve, reject) => {
        setTimeout(() => {
          const response: AxiosResponse = {
            data: outcome.envelope,
            status: outcome.status,
            statusText: outcome.status === 200 ? 'OK' : 'Unauthorized',
            headers: {},
            config,
          };
          if (outcome.status >= 400) {
            // 模拟 axios 对非 2xx 的拒绝（携带 response 供拦截器读取）。
            const error = new Error(`Request failed with status code ${outcome.status}`) as Error & {
              config: InternalAxiosRequestConfig;
              response: AxiosResponse;
              isAxiosError: boolean;
            };
            error.config = config;
            error.response = response;
            error.isAxiosError = true;
            reject(error);
          } else {
            resolve(response);
          }
        }, latencyMs());
      });
    return config;
  });
}
