package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 拒绝好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectFriendRequestReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long requestId;
    private long userId;
}
