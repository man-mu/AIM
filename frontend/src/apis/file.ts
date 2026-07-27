import { request } from './request.ts';
import type {
  ConfirmUploadData,
  ConfirmUploadParams,
  FileInfo,
  GetDownloadUrlData,
  GetUploadUrlData,
  GetUploadUrlParams,
} from '@/types/File/File';
import type { Int64 } from '@/types/User/User';

export const fileApi = {
  getUploadUrl: (data: GetUploadUrlParams) => {
    return request<GetUploadUrlData>('/files/upload-url', 'POST', data);
  },
  confirmUpload: (data: ConfirmUploadParams) => {
    return request<ConfirmUploadData>('/files/confirm', 'POST', data);
  },
  getDownloadUrl: (fileId: Int64, expiresIn = 3600, options?: { signal?: AbortSignal }) => {
    return request<GetDownloadUrlData>(`/files/${fileId}/download`, 'GET', { expiresIn }, options);
  },
  getInfo: (fileId: Int64, options?: { signal?: AbortSignal }) => {
    return request<FileInfo>(`/files/${fileId}/info`, 'GET', undefined, options);
  },
  delete: (fileId: Int64) => {
    return request(`/files/${fileId}`, 'DELETE');
  },
  /** 请求体直接是 Int64[]。 */
  batchGetInfo: (fileIds: Int64[]) => {
    return request<FileInfo[]>('/files/batch', 'POST', fileIds as unknown as object);
  },
};
