package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 禁言成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MuteMemberReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long operatorId;
    private long targetUserId;
    private long muteUntil;
}
