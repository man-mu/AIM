package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 禁言成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuteMemberReq {

    private long conversationId;
    private long operatorId;
    private long targetUserId;
    private long muteUntil;
}
