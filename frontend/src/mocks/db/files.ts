import { nextId, type DbState } from './state';
import { MockDbError } from './users';
import type { FileRow } from './schema';

/** file-service mock：三步上传（upload-url → PUT → confirm）。 */

const MAX_FILE_SIZE = 100 * 1024 * 1024;

const ALLOWED_MIME_PREFIXES = ['image/', 'video/', 'audio/', 'text/'];
const ALLOWED_MIME_EXACT = new Set([
  'application/pdf',
  'application/zip',
  'application/json',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/octet-stream',
]);

export interface CreatePendingFileInput {
  name: string;
  mimeType: string;
  size: number;
  purpose: 1 | 2;
  access: 1 | 2 | 3;
  uploaderId: string;
  expiresIn?: number;
}

export interface PendingFileResult {
  row: FileRow;
  uploadUrl: string;
  expiresAt: number;
}

export function createPendingFile(state: DbState, input: CreatePendingFileInput, now: number): PendingFileResult {
  if (input.size > MAX_FILE_SIZE) {
    throw new MockDbError(50003, '文件过大 (100MB)');
  }
  const allowed =
    ALLOWED_MIME_PREFIXES.some((prefix) => input.mimeType.startsWith(prefix)) || ALLOWED_MIME_EXACT.has(input.mimeType);
  if (!allowed) {
    throw new MockDbError(50004, '不支持的文件类型');
  }

  const ext = input.name.includes('.') ? (input.name.split('.').pop() ?? '') : '';
  const row: FileRow = {
    fileId: nextId(state, 'file'),
    name: input.name,
    key: `aim/mock/${now}/${input.name}`,
    size: input.size,
    mimeType: input.mimeType,
    ext,
    width: 0,
    height: 0,
    duration: 0,
    md5: '',
    purpose: input.purpose,
    access: input.access,
    uploaderId: input.uploaderId,
    bucket: 'aim-mock',
    status: 0,
    createdAt: now,
  };
  state.files.set(row.fileId, row);

  const expiresAt = now + (input.expiresIn ?? 3600) * 1000;
  return { row, uploadUrl: `mock://upload/${row.fileId}`, expiresAt };
}

export function requireFile(state: DbState, fileId: string): FileRow {
  const row = state.files.get(fileId);
  if (!row || row.status === 2) {
    throw new MockDbError(50001, '文件不存在');
  }
  return row;
}

export function confirmFile(state: DbState, fileId: string, md5: string | undefined, meta?: { width?: number; height?: number }): FileRow {
  const row = requireFile(state, fileId);
  row.status = 1;
  if (md5) {
    row.md5 = md5;
  }
  if (meta?.width) {
    row.width = meta.width;
  }
  if (meta?.height) {
    row.height = meta.height;
  }
  return row;
}

export function deleteFile(state: DbState, fileId: string, operatorId: string): void {
  const row = requireFile(state, fileId);
  if (row.uploaderId !== operatorId) {
    throw new MockDbError(50001, '文件不存在');
  }
  row.status = 2;
}

export function listFilesByIds(state: DbState, ids: string[]): FileRow[] {
  return ids
    .map((id) => state.files.get(id))
    .filter((row): row is FileRow => Boolean(row) && (row as FileRow).status !== 2);
}
