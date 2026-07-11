package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拒绝好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectFriendRequestReq {

    private long requestId;
    private long userId;
}
