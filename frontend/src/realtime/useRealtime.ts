import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { matchPath, useLocation } from 'react-router';
import { convApi } from '@/apis/conv';
import { useRealtimeStore } from '@/stores/useRealtimeStore';
import { createRealtimeDispatcher } from './dispatcher';
import { getRealtimeChannel } from './index';

/**
 * 实时层生命周期（挂载于登录态 AppLayout）：
 *  - 连接通道，帧 → dispatcher；
 *  - “当前激活会话”取自 URL（/home/:conversationId），用 ref 提供给 dispatcher；
 *  - 窗口可见性（Page Visibility API）决定新消息是否计未读。
 */
export function useRealtime(): void {
  const queryClient = useQueryClient();
  const location = useLocation();
  const setStatus = useRealtimeStore((state) => state.setStatus);
  const activeConversationRef = useRef<string | null>(null);

  const match = matchPath('/home/:conversationId', location.pathname);
  activeConversationRef.current = match?.params.conversationId ?? null;

  useEffect(() => {
    const channel = getRealtimeChannel();
    const dispatcher = createRealtimeDispatcher({
      queryClient,
      getActiveConversationId: () => activeConversationRef.current,
      isWindowVisible: () => typeof document === 'undefined' || document.visibilityState === 'visible',
      onAutoRead: (conversationId, seq) => {
        void convApi.markRead(conversationId, seq).catch(() => undefined);
      },
    });

    const offFrame = channel.onFrame((frame) => dispatcher.handleFrame(frame));
    const offStatus = channel.onStatus(setStatus);
    channel.connect();
    setStatus(channel.getStatus());

    return () => {
      offFrame();
      offStatus();
      channel.disconnect();
    };
  }, [queryClient, setStatus]);
}
