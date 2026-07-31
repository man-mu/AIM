import type { Int64 } from '../User/User';

/** 文件用途：1=消息附件 2=头像。 */
export type FilePurpose = 1 | 2;

/** 访问级别：1=私有 2=会话内可见 3=公开。 */
export type FileAccess = 1 | 2 | 3;

/** 文件状态：0=PENDING 1=CONFIRMED 2=DELETED。 */
export type FileStatus = 0 | 1 | 2;

/** FileInfo（api-v1.md 附录 C）。 */
export interface FileInfo {
  fileId: Int64;
  name: string;
  key: string;
  size: number;
  mimeType: string;
  ext: string;
  width: number;
  height: number;
  duration: number;
  md5: string;
  purpose: FilePurpose;
  access: FileAccess;
  uploaderId: Int64;
  bucket: string;
  status: FileStatus;
  createdAt: number;
}

export interface GetUploadUrlParams {
  name: string;
  mimeType: string;
  size: number;
  purpose: FilePurpose;
  access: FileAccess;
  expiresIn?: number;
}

export interface GetUploadUrlData {
  fileId: Int64;
  uploadUrl: string;
  key: string;
  expiresAt: number;
}

export interface ConfirmUploadParams {
  fileId: Int64;
  md5?: string;
}

export interface ConfirmUploadData {
  file: FileInfo;
}

export interface GetDownloadUrlData {
  downloadUrl: string;
  expiresAt: number;
  file: FileInfo;
}
