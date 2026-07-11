package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同意好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcceptFriendRequestReq {

    private long requestId;
    private long userId;
}
