import { ok } from '@/lib/result';
import { mockBlobStore } from '../blobStore';
import type { MockHandler } from '../engine/types';
import { asId, asIdArray, asNumber, asRecord, asString, type HandlerContext } from './context';

/** File 域 handlers（api-v1-implemented.md §5）。 */
export function createFileRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const uploadUrl: MockHandler = (request) => {
    const body = asRecord(request.body);
    const result = ctx.db.files.createPending(
      {
        name: asString(body.name, 'file.bin'),
        mimeType: asString(body.mimeType, 'application/octet-stream'),
        size: asNumber(body.size),
        purpose: (asNumber(body.purpose, 1) === 2 ? 2 : 1) as 1 | 2,
        access: ([1, 2, 3].includes(asNumber(body.access, 2)) ? asNumber(body.access, 2) : 2) as 1 | 2 | 3,
        uploaderId: request.userId as string,
        expiresIn: asNumber(body.expiresIn, 3600),
      },
      ctx.now(),
    );
    return ok({
      fileId: result.row.fileId,
      uploadUrl: result.uploadUrl,
      key: result.row.key,
      expiresAt: result.expiresAt,
    });
  };

  const confirm: MockHandler = (request) => {
    const body = asRecord(request.body);
    const fileId = asId(body.fileId);
    const md5 = asString(body.md5) || undefined;
    const file = ctx.db.files.confirm(fileId, md5);
    return ok({ file });
  };

  const download: MockHandler = (request) => {
    const fileId = asId(request.params.fileId);
    const file = ctx.db.files.require(fileId);
    const expiresIn = asNumber(request.query.expiresIn, 3600);
    return ok({
      downloadUrl: mockBlobStore.getUrl(fileId) ?? `mock://file/${fileId}`,
      expiresAt: ctx.now() + expiresIn * 1000,
      file,
    });
  };

  const info: MockHandler = (request) => {
    return ok(ctx.db.files.require(asId(request.params.fileId)));
  };

  const remove: MockHandler = (request) => {
    ctx.db.files.delete(asId(request.params.fileId), request.userId as string);
    mockBlobStore.revoke(asId(request.params.fileId));
    return ok(null);
  };

  const batch: MockHandler = (request) => {
    // 请求体直接是 long[] 数组。
    return ok(ctx.db.files.listByIds(asIdArray(request.body)));
  };

  return [
    ['POST /files/upload-url', uploadUrl],
    ['POST /files/confirm', confirm],
    ['POST /files/batch', batch],
    ['GET /files/:fileId/download', download],
    ['GET /files/:fileId/info', info],
    ['DELETE /files/:fileId', remove],
  ];
}
