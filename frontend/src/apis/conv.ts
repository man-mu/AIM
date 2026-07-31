import { request } from './request.ts';
import type {
  ConversationDTO,
  ConversationSettingsData,
  CreateConversationData,
  CreateConversationParams,
  GetMembersData,
  InviteMembersData,
  ListConversationsData,
  ListConversationsParams,
  UpdateSettingsParams,
} from '@/types/Conversation/Conversation';
import type { Int64 } from '@/types/User/User';

export const convApi = {
  create: (data: CreateConversationParams) => {
    return request<CreateConversationData>('/convs', 'POST', data);
  },
  getDetail: (conversationId: Int64, options?: { signal?: AbortSignal }) => {
    return request<ConversationDTO>(`/convs/${conversationId}`, 'GET', undefined, options);
  },
  list: (params?: ListConversationsParams, options?: { signal?: AbortSignal }) => {
    // 契约 §5：默认 pageSize=20（上限 100）。
    return request<ListConversationsData>('/convs', 'GET', { pageNum: 1, pageSize: 20, ...params }, options);
  },
  getMembers: (conversationId: Int64, params?: { pageNum?: number; pageSize?: number }, options?: { signal?: AbortSignal }) => {
    // 契约 §5：默认 pageSize=50（上限 100）。
    return request<GetMembersData>(`/convs/${conversationId}/members`, 'GET', { pageNum: 1, pageSize: 50, ...params }, options);
  },
  invite: (conversationId: Int64, userIds: Int64[]) => {
    return request<InviteMembersData>(`/convs/${conversationId}/members/invite`, 'POST', { userIds });
  },
  kick: (conversationId: Int64, userIds: Int64[]) => {
    return request(`/convs/${conversationId}/members/kick`, 'POST', { userIds });
  },
  muteMember: (conversationId: Int64, userId: Int64, durationSeconds: number) => {
    return request(`/convs/${conversationId}/members/${userId}/mute`, 'PUT', { durationSeconds });
  },
  unmuteMember: (conversationId: Int64, userId: Int64) => {
    return request(`/convs/${conversationId}/members/${userId}/mute`, 'DELETE');
  },
  transferOwner: (conversationId: Int64, newOwnerId: Int64) => {
    return request(`/convs/${conversationId}/transfer`, 'POST', { newOwnerId });
  },
  setAnnouncement: (conversationId: Int64, content: string) => {
    return request(`/convs/${conversationId}/announcement`, 'PUT', { content });
  },
  deleteAnnouncement: (conversationId: Int64) => {
    return request(`/convs/${conversationId}/announcement`, 'DELETE');
  },
  getSettings: (conversationId: Int64, options?: { signal?: AbortSignal }) => {
    return request<ConversationSettingsData>(`/convs/${conversationId}/settings`, 'GET', undefined, options);
  },
  updateSettings: (conversationId: Int64, data: UpdateSettingsParams) => {
    return request(`/convs/${conversationId}/settings`, 'PUT', data);
  },
  markRead: (conversationId: Int64, seq: number) => {
    return request(`/convs/${conversationId}/read`, 'PUT', { seq });
  },
};
