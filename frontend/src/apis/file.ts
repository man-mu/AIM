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
  /** expiresIn 契约明确「无效，服务端固定 1800s」——保留该字段仅作兼容，服务端会忽略。 */
  getUploadUrl: (data: GetUploadUrlParams) => {
    return request<GetUploadUrlData>('/files/upload-url', 'POST', data);
  },
  confirmUpload: (data: ConfirmUploadParams) => {
    return request<ConfirmUploadData>('/files/confirm', 'POST', data);
  },
  /** 契约 §7：下载为 PathVariable 无 query；expiresIn 保留仅兼容旧调用（服务端忽略）。 */
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
