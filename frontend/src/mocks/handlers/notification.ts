import { ok } from '@/lib/result';
import type { MockHandler } from '../engine/types';
import { asId, asNumber, pageParams, paginate, type HandlerContext } from './context';

/** Notification 域 handlers（api-v1.md §8，规划态接口的 mock 实现）。 */
export function createNotificationRoutes(
  ctx: HandlerContext,
): Array<[string, MockHandler, { isPublic?: boolean }?]> {
  const list: MockHandler = (request) => {
    const type = asNumber(request.query.type, 0);
    const isReadRaw = request.query.isRead;
    const filter: { type?: number; isRead?: boolean } = {};
    if (type !== 0) {
      filter.type = type;
    }
    if (isReadRaw === 'true' || isReadRaw === 'false') {
      filter.isRead = isReadRaw === 'true';
    }

    const rows = ctx.db.social.listNotifications(request.userId as string, filter);
    const { pageNum, pageSize } = pageParams(request.query);
    const { slice, total } = paginate(rows, pageNum, pageSize);
    return ok({ list: slice, total, pageNum, pageSize });
  };

  const unreadCount: MockHandler = (request) => {
    return ok({ count: ctx.db.social.unreadNotificationCount(request.userId as string) });
  };

  const markRead: MockHandler = (request) => {
    ctx.db.social.markNotificationRead(request.userId as string, asId(request.params.notificationId));
    return ok(null);
  };

  const readAll: MockHandler = (request) => {
    ctx.db.social.markAllNotificationsRead(request.userId as string);
    return ok(null);
  };

  const remove: MockHandler = (request) => {
    ctx.db.social.deleteNotification(request.userId as string, asId(request.params.notificationId));
    return ok(null);
  };

  return [
    ['GET /notifications', list],
    ['GET /notifications/unread-count', unreadCount],
    ['POST /notifications/read-all', readAll],
    ['POST /notifications/:notificationId/read', markRead],
    ['DELETE /notifications/:notificationId', remove],
  ];
}
