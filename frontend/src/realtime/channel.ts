import type { RealtimeFrame, UpstreamEvent } from './protocol';

/**
 * 实时通道抽象：上层（dispatcher / hooks）只依赖本接口。
 * 实现：mockChannel（Mock 平台事件源）与 wsChannel（真实 WebSocket，Phase B）。
 */
export type ChannelStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface RealtimeChannel {
  connect(): void;
  disconnect(): void;
  send(event: UpstreamEvent): void;
  onFrame(listener: (frame: RealtimeFrame) => void): () => void;
  onStatus(listener: (status: ChannelStatus) => void): () => void;
  getStatus(): ChannelStatus;
}
