package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查是否被拉黑请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsBlockedReq {

    private long userId;
    private long targetUserId;
}
