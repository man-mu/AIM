import { createEmitter } from '@/lib/emitter';
import { subscribeMockRealtime } from '@/mocks';
import type { ChannelStatus, RealtimeChannel } from './channel';
import type { RealtimeFrame } from './protocol';

/**
 * Mock 通道：把 MockRealtimeHub 适配成 RealtimeChannel。
 * 连接即 open（无网络握手），上行事件按需回应（ping→pong）。
 */
export function createMockChannel(): RealtimeChannel {
  const emitter = createEmitter<{ frame: RealtimeFrame; status: ChannelStatus }>();
  let status: ChannelStatus = 'idle';
  let unsubscribe: (() => void) | null = null;

  const setStatus = (next: ChannelStatus): void => {
    status = next;
    emitter.emit('status', next);
  };

  return {
    connect() {
      if (status === 'open') {
        return;
      }
      setStatus('connecting');
      unsubscribe = subscribeMockRealtime((frame) => emitter.emit('frame', frame));
      setStatus('open');
    },
    disconnect() {
      unsubscribe?.();
      unsubscribe = null;
      setStatus('closed');
    },
    send(event) {
      if (event.event === 'ping') {
        emitter.emit('frame', { event: 'pong', data: {}, timestamp: Date.now() });
      }
      // typing / ack 等上行事件在 mock 模式下无需处理：
      // NPC 的行为由 hub 剧本驱动。
    },
    onFrame: (listener) => emitter.on('frame', listener),
    onStatus: (listener) => emitter.on('status', listener),
    getStatus: () => status,
  };
}
