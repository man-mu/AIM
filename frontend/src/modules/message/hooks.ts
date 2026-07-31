import type { InfiniteData } from '@tanstack/react-query';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { messageApi } from '@/apis/message';
import { queryKeys } from '@/apis/queryKeys';
import { toast } from '@/components/ui/toast/toastBus';
import { newClientMsgId, toInt64String } from '@/lib/ids';
import { isApiError } from '@/lib/result';
import { applyIncomingPreview } from '@/modules/conversation/cache';
import type { UiConversation } from '@/modules/conversation/model';
import { usePendingMessagesStore } from '@/stores/usePendingMessagesStore';
import { useAuthStore } from '@/stores/useAuthStore';
import { useComposerStore } from '@/stores/useComposerStore';
import type { ListMessagesData, MessageContent, MsgType } from '@/types/Message/Message';
import {
  appendIncoming,
  applyEdited,
  applyRecalled,
  dtoFromAck,
  flattenAscending,
  removeMessage,
  type MessagesCache,
} from './cache';
import { createPendingMessage, previewOfContent, type UiMessage } from './model';

/** 游标分页：pages[0]=最新页；fetchNextPage 拉更早历史。 */
export function useMessagesQuery(conversationId: string | null) {
  // TanStack Query v5 泛型顺序：TQueryFnData, TError, TData, TQueryKey, TPageParam
  // TData 必须为 InfiniteData 形态才能与 MessagesCache（flattenAscending 入参）匹配
  return useInfiniteQuery<
    ListMessagesData,
    Error,
    InfiniteData<ListMessagesData, string>,
    readonly ['messages', 'pages', string],
    string
  >({
    queryKey: queryKeys.messages.pages(conversationId ?? 'none'),
    initialPageParam: '0',
    queryFn: ({ pageParam, signal }) =>
      messageApi.list(conversationId as string, { cursor: pageParam, limit: 20 }, { signal }),
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor : undefined),
    enabled: conversationId !== null,
    staleTime: 15_000,
  });
}

/** 服务端分页 + 本地乐观队列 → 升序渲染列表。 */
export function useVisibleMessages(conversationId: string | null): UiMessage[] {
  const query = useMessagesQuery(conversationId);
  const pending = usePendingMessagesStore((state) =>
    conversationId ? (state.byConv[conversationId] ?? EMPTY_PENDING) : EMPTY_PENDING,
  );

  return useMemo(() => flattenAscending(query.data, pending), [query.data, pending]);
}

const EMPTY_PENDING: UiMessage[] = [];

interface SendInput {
  msgType: MsgType;
  content: MessageContent;
  replyToId?: string;
  replyToPreview?: string;
  /** 重发失败消息时复用原 clientMsgId（幂等）。 */
  clientMsgId?: string;
}

/**
 * 乐观发送管线：
 *  占位入队(sending) → 服务端确认 → 占位出队 + DTO 进缓存 + 会话列表预览更新
 *  失败 → 占位标红(failed) 支持重发；40004(重复) 视为已送达。
 */
export function useSendMessage(conversationId: string | null) {
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const pendingStore = usePendingMessagesStore;
  const clearComposer = useComposerStore((state) => state.clearComposer);

  return useCallback(
    async (input: SendInput): Promise<boolean> => {
      if (!conversationId || !currentUser) {
        return false;
      }
      const senderId = toInt64String(currentUser.id);
      const clientMsgId = input.clientMsgId ?? newClientMsgId();
      const isRetry = Boolean(input.clientMsgId);

      if (isRetry) {
        pendingStore.getState().markSending(conversationId, clientMsgId);
      } else {
        pendingStore.getState().add(
          createPendingMessage({
            conversationId,
            senderId,
            msgType: input.msgType,
            content: input.content,
            replyToId: input.replyToId,
            replyToPreview: input.replyToPreview,
            clientMsgId,
            createdAt: Date.now(),
          }),
        );
        clearComposer(conversationId);
      }

      try {
        const ack = await messageApi.send({
          conversationId,
          msgType: input.msgType,
          content: input.content,
          replyToId: input.replyToId ?? '0',
          clientMsgId,
        });

        const pendingList = pendingStore.getState().byConv[conversationId] ?? [];
        const pendingMessage = pendingList.find((item) => item.clientMsgId === clientMsgId);
        pendingStore.getState().remove(conversationId, clientMsgId);

        if (pendingMessage) {
          const dto = dtoFromAck(pendingMessage, {
            messageId: toInt64String(ack.messageId),
            seq: ack.seq,
            createdAt: ack.createdAt,
          });
          queryClient.setQueryData<MessagesCache>(queryKeys.messages.pages(conversationId), (old) =>
            appendIncoming(old, dto),
          );
          queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
            applyIncomingPreview(old, {
              conversationId,
              preview: previewOfContent(input.msgType, input.content),
              at: ack.createdAt,
              unreadCount: 0,
              maxSeq: ack.seq,
            }),
          );
        }
        return true;
      } catch (error) {
        if (isApiError(error) && error.code === 40004) {
          // 服务端已收到过这条消息：按成功处理，拉最新页对齐。
          pendingStore.getState().remove(conversationId, clientMsgId);
          void queryClient.invalidateQueries({ queryKey: queryKeys.messages.pages(conversationId) });
          return true;
        }
        pendingStore.getState().markFailed(conversationId, clientMsgId);
        toast.error(error instanceof Error ? error.message : '发送失败');
        return false;
      }
    },
    [clearComposer, conversationId, currentUser, pendingStore, queryClient],
  );
}

/**
 * 附件发送（图片/文件）：
 * 选择文件 → 立刻插入带本地预览的乐观气泡（objectURL）→
 * Worker 哈希 + 三步上传（进度写回气泡）→ 携 fileId 正式发送。
 */
export function useSendAttachment(conversationId: string | null) {
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const pendingStore = usePendingMessagesStore;

  return useCallback(
    async (file: File): Promise<boolean> => {
      if (!conversationId || !currentUser) {
        return false;
      }
      const { uploadFile } = await import('@/modules/file/useFileUpload');
      const { readImageSize } = await import('@/modules/file/image');

      const senderId = toInt64String(currentUser.id);
      const clientMsgId = newClientMsgId();
      const isImage = file.type.startsWith('image/');
      const msgType: MsgType = isImage ? 2 : 3;
      const localUrl = URL.createObjectURL(file);
      const size = isImage ? await readImageSize(file) : { width: 0, height: 0 };

      const previewContent: MessageContent = isImage
        ? { fileId: '0', url: localUrl, thumbnailUrl: localUrl, width: size.width, height: size.height, size: file.size, format: file.type.split('/')[1] ?? '' }
        : { fileId: '0', url: localUrl, name: file.name, size: file.size, ext: file.name.split('.').pop() ?? '', mimeType: file.type };

      pendingStore.getState().add(
        createPendingMessage({
          conversationId,
          senderId,
          msgType,
          content: previewContent,
          clientMsgId,
          createdAt: Date.now(),
        }),
      );

      try {
        const uploaded = await uploadFile(file, {
          purpose: 1,
          access: 2,
          onProgress: (percent) => pendingStore.getState().setProgress(conversationId, clientMsgId, percent),
        });

        const finalContent: MessageContent = isImage
          ? { ...previewContent, fileId: toInt64String(uploaded.file.fileId), url: uploaded.url, thumbnailUrl: uploaded.url }
          : { ...previewContent, fileId: toInt64String(uploaded.file.fileId), url: uploaded.url };

        const ack = await messageApi.send({
          conversationId,
          msgType,
          content: finalContent,
          replyToId: '0',
          clientMsgId,
        });

        const pendingList = pendingStore.getState().byConv[conversationId] ?? [];
        const pendingMessage = pendingList.find((item) => item.clientMsgId === clientMsgId);
        pendingStore.getState().remove(conversationId, clientMsgId);
        if (pendingMessage) {
          const dto = dtoFromAck({ ...pendingMessage, content: finalContent }, {
            messageId: toInt64String(ack.messageId),
            seq: ack.seq,
            createdAt: ack.createdAt,
          });
          queryClient.setQueryData<MessagesCache>(queryKeys.messages.pages(conversationId), (old) =>
            appendIncoming(old, dto),
          );
          queryClient.setQueryData<UiConversation[]>(queryKeys.conversations.list, (old) =>
            applyIncomingPreview(old, {
              conversationId,
              preview: previewOfContent(msgType, finalContent),
              at: ack.createdAt,
              unreadCount: 0,
              maxSeq: ack.seq,
            }),
          );
        }
        return true;
      } catch (error) {
        pendingStore.getState().markFailed(conversationId, clientMsgId);
        toast.error(error instanceof Error ? error.message : '发送附件失败');
        return false;
      }
    },
    [conversationId, currentUser, pendingStore, queryClient],
  );
}

export function useMessageActions(conversationId: string | null) {
  const queryClient = useQueryClient();
  const key = queryKeys.messages.pages(conversationId ?? 'none');
  const onError = (error: unknown): void => {
    toast.error(error instanceof Error ? error.message : '操作失败');
  };

  const recall = useMutation({
    mutationFn: (messageId: string) => messageApi.recall(messageId),
    onSuccess: (_data, messageId) => {
      queryClient.setQueryData<MessagesCache>(key, (old) => applyRecalled(old, messageId));
    },
    onError,
  });

  const edit = useMutation({
    mutationFn: ({ messageId, newContent }: { messageId: string; newContent: MessageContent }) =>
      messageApi.edit(messageId, { newContent }),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<MessagesCache>(key, (old) =>
        applyEdited(old, variables.messageId, variables.newContent, Date.now()),
      );
    },
    onError,
  });

  const remove = useMutation({
    mutationFn: ({ messageId, deleteForAll }: { messageId: string; deleteForAll: boolean }) =>
      messageApi.delete(messageId, deleteForAll),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<MessagesCache>(key, (old) => removeMessage(old, variables.messageId));
    },
    onError,
  });

  return { recall, edit, remove };
}

/**
 * 打开会话即视为已读：
 * 会话激活 && 页面可见 && 存在未读 → 防抖上报 markRead。
 * 页面从后台切回（Page Visibility API）时补一次。
 */
export function useAutoMarkRead(
  conversation: { id: string; maxSeq: number; unreadCount: number } | null,
  markRead: (input: { conversationId: string; seq: number }) => void,
): void {
  const lastReportedRef = useRef<{ id: string; seq: number } | null>(null);

  useEffect(() => {
    if (!conversation) {
      return undefined;
    }

    const report = (): void => {
      if (typeof document !== 'undefined' && document.visibilityState !== 'visible') {
        return;
      }
      const last = lastReportedRef.current;
      if (conversation.unreadCount === 0 && last?.id === conversation.id && last.seq >= conversation.maxSeq) {
        return;
      }
      if (conversation.maxSeq <= 0) {
        return;
      }
      if (last?.id === conversation.id && last.seq >= conversation.maxSeq) {
        return;
      }
      lastReportedRef.current = { id: conversation.id, seq: conversation.maxSeq };
      markRead({ conversationId: conversation.id, seq: conversation.maxSeq });
    };

    const timer = setTimeout(report, 400);
    const onVisibility = (): void => report();
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [conversation, markRead]);
}
