package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestReq {

    private long fromUserId;
    private long toUserId;
    private String message;
}
