/**
 * 多标签页同步（BroadcastChannel）：
 * 任一标签页登出，其余标签页立即清理本地会话并回到登录页，
 * 避免“一个 tab 退出、另一个 tab 还挂着旧 token”的割裂状态。
 */
export type TabSyncMessage = { type: 'logout' } | { type: 'conversation-read'; conversationId: string };

const CHANNEL_NAME = 'aim-tab-sync';

interface TabSync {
  post(message: TabSyncMessage): void;
  subscribe(listener: (message: TabSyncMessage) => void): () => void;
}

function createTabSync(): TabSync {
  if (typeof BroadcastChannel === 'undefined') {
    return { post: () => undefined, subscribe: () => () => undefined };
  }

  return {
    post(message) {
      // 每次新建，避免持有长生命周期通道；发送后立即关闭。
      const channel = new BroadcastChannel(CHANNEL_NAME);
      channel.postMessage(message);
      channel.close();
    },
    subscribe(listener) {
      const channel = new BroadcastChannel(CHANNEL_NAME);
      const handler = (event: MessageEvent): void => {
        const data = event.data as TabSyncMessage | undefined;
        if (data && typeof data.type === 'string') {
          listener(data);
        }
      };
      channel.addEventListener('message', handler);
      return () => {
        channel.removeEventListener('message', handler);
        channel.close();
      };
    },
  };
}

export const tabSync: TabSync = createTabSync();
