package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送好友申请请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fromUserId;
    private long toUserId;
    private String message;
}
