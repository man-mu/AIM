import { request } from './request.ts';
import type {
  EditMessageParams,
  ListMessagesData,
  SendMessageData,
  SendMessageParams,
  SyncMessagesData,
} from '@/types/Message/Message';
import type { Int64 } from '@/types/User/User';

/**
 * Message API（api-v1.md §6 规划态接口，当前由 Mock 平台实现）。
 * 后端 message-service 上线后本文件无需改动。
 */
export const messageApi = {
  send: (data: SendMessageParams) => {
    return request<SendMessageData>('/messages/send', 'POST', data);
  },
  list: (
    conversationId: Int64,
    params: { cursor: string; limit?: number },
    options?: { signal?: AbortSignal },
  ) => {
    return request<ListMessagesData>(`/messages/${conversationId}`, 'GET', { limit: 20, ...params }, options);
  },
  sync: (conversationId: Int64, params: { fromSeq: number; limit?: number }, options?: { signal?: AbortSignal }) => {
    return request<SyncMessagesData>(`/messages/${conversationId}/sync`, 'GET', { limit: 50, ...params }, options);
  },
  recall: (messageId: Int64) => {
    return request(`/messages/${messageId}/recall`, 'POST', {});
  },
  edit: (messageId: Int64, data: EditMessageParams) => {
    return request(`/messages/${messageId}`, 'PUT', data);
  },
  delete: (messageId: Int64, deleteForAll: boolean) => {
    return request(`/messages/${messageId}`, 'DELETE', { deleteForAll });
  },
};
