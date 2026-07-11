package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送好友申请响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendRequestResp {

    private long requestId;
}
