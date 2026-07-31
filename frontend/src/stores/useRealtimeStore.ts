import { create } from 'zustand';
import type { ChannelStatus } from '@/realtime/channel';

/** 实时连接状态（顶栏指示器 / 断线重连提示用）。 */
interface RealtimeState {
  status: ChannelStatus;
  setStatus: (status: ChannelStatus) => void;
}

export const useRealtimeStore = create<RealtimeState>((set) => ({
  status: 'idle',
  setStatus: (status) => set({ status }),
}));
