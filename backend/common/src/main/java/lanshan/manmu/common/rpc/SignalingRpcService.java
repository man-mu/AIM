package lanshan.manmu.common.rpc;

import java.util.List;
import lanshan.manmu.common.rpc.dto.signaling.*;

/**
 * 信令推送服务 Dubbo 接口。
 */
public interface SignalingRpcService {

    ListNotificationsResp listNotifications(ListNotificationsReq req);
    long getUnreadCount(long userId);
    void markRead(long notificationId, long userId);
    void markAllRead(long userId);
    void deleteNotification(long notificationId);
    boolean isOnline(long userId);
    BatchIsOnlineResp batchIsOnline(List<Long> userIds);
}
