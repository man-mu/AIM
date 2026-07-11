package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查是否被拉黑响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsBlockedResp {

    private boolean isBlocked;
}
