package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送好友申请响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long requestId;
}
