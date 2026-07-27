import { createBackoffPolicy } from '@/lib/backoff';
import { systemScheduler, type Scheduler } from '@/lib/clock';
import { createEmitter } from '@/lib/emitter';
import type { ChannelStatus, RealtimeChannel } from './channel';
import type { RealtimeFrame, UpstreamEvent } from './protocol';

/**
 * 真实 WebSocket 通道（Phase B，后端 ws-gateway 就绪即接入）。
 *
 * - 心跳：每 30s ping，90s 无任何下行帧判定断线；
 * - 重连：指数退避（1s→2s→4s→…封顶 30s，带抖动）；
 * - 依赖注入：WebSocket 构造器与调度器均可注入，逻辑可在 Node 中单测。
 */
export interface WsChannelOptions {
  /** 完整连接地址（含 token/device_id query）。 */
  buildUrl(): string;
  scheduler?: Scheduler;
  webSocketCtor?: typeof WebSocket;
  pingIntervalMs?: number;
  deadIntervalMs?: number;
}

export function createWsChannel(options: WsChannelOptions): RealtimeChannel {
  const scheduler = options.scheduler ?? systemScheduler;
  const WebSocketCtor = options.webSocketCtor ?? (typeof WebSocket !== 'undefined' ? WebSocket : undefined);
  const pingIntervalMs = options.pingIntervalMs ?? 30_000;
  const deadIntervalMs = options.deadIntervalMs ?? 90_000;
  const backoff = createBackoffPolicy({ baseMs: 1_000, maxMs: 30_000 });

  const emitter = createEmitter<{ frame: RealtimeFrame; status: ChannelStatus }>();
  let status: ChannelStatus = 'idle';
  let socket: WebSocket | null = null;
  let manualClose = false;
  let attempt = 0;
  let lastFrameAt = 0;
  let cancelHeartbeat: (() => void) | null = null;
  let cancelReconnect: (() => void) | null = null;

  const setStatus = (next: ChannelStatus): void => {
    if (status !== next) {
      status = next;
      emitter.emit('status', next);
    }
  };

  const stopHeartbeat = (): void => {
    cancelHeartbeat?.();
    cancelHeartbeat = null;
  };

  const startHeartbeat = (): void => {
    stopHeartbeat();
    const tick = (): void => {
      if (!socket || socket.readyState !== 1) {
        return;
      }
      if (scheduler.now() - lastFrameAt >= deadIntervalMs) {
        // 假死连接：主动关闭触发重连。
        socket.close();
        return;
      }
      send({ event: 'ping', data: {} });
      cancelHeartbeat = scheduler.schedule(tick, pingIntervalMs);
    };
    cancelHeartbeat = scheduler.schedule(tick, pingIntervalMs);
  };

  const scheduleReconnect = (): void => {
    if (manualClose) {
      return;
    }
    attempt += 1;
    setStatus('reconnecting');
    cancelReconnect = scheduler.schedule(openSocket, backoff.delayFor(attempt));
  };

  const openSocket = (): void => {
    if (!WebSocketCtor) {
      console.error('[wsChannel] WebSocket is not available in this environment');
      setStatus('closed');
      return;
    }
    setStatus(attempt === 0 ? 'connecting' : 'reconnecting');
    const ws = new WebSocketCtor(options.buildUrl());
    socket = ws;

    ws.onopen = () => {
      attempt = 0;
      lastFrameAt = scheduler.now();
      setStatus('open');
      startHeartbeat();
    };
    ws.onmessage = (event: MessageEvent) => {
      lastFrameAt = scheduler.now();
      try {
        const frame = JSON.parse(String(event.data)) as RealtimeFrame;
        if (frame && typeof frame.event === 'string') {
          emitter.emit('frame', frame);
        }
      } catch {
        console.warn('[wsChannel] non-JSON frame ignored');
      }
    };
    ws.onclose = () => {
      stopHeartbeat();
      socket = null;
      if (manualClose) {
        setStatus('closed');
      } else {
        scheduleReconnect();
      }
    };
    ws.onerror = () => {
      // onclose 会随后触发，统一走重连路径。
    };
  };

  const send = (event: UpstreamEvent): void => {
    if (socket && socket.readyState === 1) {
      socket.send(JSON.stringify({ ...event, timestamp: scheduler.now() }));
    }
  };

  return {
    connect() {
      if (status === 'open' || status === 'connecting') {
        return;
      }
      manualClose = false;
      attempt = 0;
      openSocket();
    },
    disconnect() {
      manualClose = true;
      cancelReconnect?.();
      cancelReconnect = null;
      stopHeartbeat();
      socket?.close();
      socket = null;
      setStatus('closed');
    },
    send,
    onFrame: (listener) => emitter.on('frame', listener),
    onStatus: (listener) => emitter.on('status', listener),
    getStatus: () => status,
  };
}
