import { request } from '@/apis/request.ts';
import type {
  BatchGetUsersData,
  Int64,
  ProfileData,
  SearchUsersData,
  SearchUsersParams,
  UpdateData,
  UpdateParams,
  UpdatePasswordParams,
  UserInfo,
} from '@/types/User/User.ts';

export const userApi = {
  getProfile: (options?: { signal?: AbortSignal }) => {
    return request<ProfileData>('/users/me', 'GET', undefined, options);
  },
  updateProfile: (data: UpdateParams) => {
    return request<UpdateData>('/users/me', 'PUT', data);
  },
  updatePassword: (data: UpdatePasswordParams) => {
    return request('/users/me/password', 'PUT', data);
  },
  getUser: (userId: Int64, options?: { signal?: AbortSignal }) => {
    return request<UserInfo>(`/users/${userId}`, 'GET', undefined, options);
  },
  /** 请求体直接是 Int64[]（非对象包裹）。 */
  batchGetUsers: (userIds: Int64[]) => {
    return request<BatchGetUsersData>('/users/batch', 'POST', userIds as unknown as object);
  },
  /** 注意：POST 但参数走 query。 */
  searchUsers: (params: SearchUsersParams, options?: { signal?: AbortSignal }) => {
    const query = new URLSearchParams({
      keyword: params.keyword,
      pageNum: String(params.pageNum ?? 1),
      pageSize: String(params.pageSize ?? 20),
    });
    return request<SearchUsersData>(`/users/search?${query.toString()}`, 'POST', undefined, options);
  },
};
