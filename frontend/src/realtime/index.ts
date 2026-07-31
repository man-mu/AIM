import { appEnv } from '@/config/env';
import { getDeviceId } from '@/utils/device';
import { storage } from '@/utils/storage';
import type { RealtimeChannel } from './channel';
import { createMockChannel } from './mockChannel';
import { createWsChannel } from './wsChannel';

/**
 * 通道工厂（进程级单例）：
 * mock 模式 → MockRealtimeHub；真实模式 → WebSocket（Phase B 后端就绪即用）。
 */
let channel: RealtimeChannel | null = null;

export function getRealtimeChannel(): RealtimeChannel {
  if (channel) {
    return channel;
  }
  channel = appEnv.useMock
    ? createMockChannel()
    : createWsChannel({
        buildUrl: () => {
          const token = storage.getAccessToken() ?? '';
          const query = new URLSearchParams({ token, device_id: getDeviceId() });
          return `${appEnv.wsUrl}?${query.toString()}`;
        },
      });
  return channel;
}
