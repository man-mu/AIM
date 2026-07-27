/**
 * TanStack Query key 注册表：全部 key 由此产出，杜绝魔法字符串。
 *
 * 约定：`[域, 实体, ...参数]`，参数一律使用字符串化的 Int64。
 * 失效粒度：invalidate(queryKeys.conversations.all) 可整域刷新。
 */
export const queryKeys = {
  /** 与 useUser/useAuth 既有缓存键保持一致。 */
  me: ['user'] as const,
  users: {
    all: ['users'] as const,
    detail: (userId: string) => ['users', 'detail', userId] as const,
    search: (keyword: string, pageNum: number) => ['users', 'search', keyword, pageNum] as const,
  },
  conversations: {
    all: ['conversations'] as const,
    list: ['conversations', 'list'] as const,
    detail: (conversationId: string) => ['conversations', 'detail', conversationId] as const,
    members: (conversationId: string) => ['conversations', 'members', conversationId] as const,
    settings: (conversationId: string) => ['conversations', 'settings', conversationId] as const,
  },
  messages: {
    all: ['messages'] as const,
    pages: (conversationId: string) => ['messages', 'pages', conversationId] as const,
  },
  friends: {
    all: ['friends'] as const,
    list: ['friends', 'list'] as const,
    groups: ['friends', 'groups'] as const,
    incomingRequests: ['friends', 'requests', 'incoming'] as const,
    outgoingRequests: ['friends', 'requests', 'outgoing'] as const,
    blacklist: ['friends', 'blacklist'] as const,
  },
  notifications: {
    all: ['notifications'] as const,
    list: ['notifications', 'list'] as const,
    unreadCount: ['notifications', 'unread-count'] as const,
  },
  files: {
    detail: (fileId: string) => ['files', 'detail', fileId] as const,
  },
} as const;
