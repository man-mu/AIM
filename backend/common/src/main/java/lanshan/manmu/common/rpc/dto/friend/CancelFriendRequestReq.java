package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 取消好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelFriendRequestReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long requestId;
    private long userId;
}
