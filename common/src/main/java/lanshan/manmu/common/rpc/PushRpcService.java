package lanshan.manmu.common.rpc;

import java.util.List;
import lanshan.manmu.common.rpc.dto.push.PushMessage;

/**
 * ws-gateway 内部推送接口。
 * 被 signaling-service 通过 Dubbo 调用。
 */
public interface PushRpcService {

    /** 推送给指定用户列表（在线则发 WS frame） */
    void pushToUsers(List<Long> userIds, PushMessage message);
    /** 推送给会话所有成员 */
    void pushToConv(long convId, PushMessage message);
    /** 推送给单个用户 */
    void pushToUser(long userId, PushMessage message);
}
