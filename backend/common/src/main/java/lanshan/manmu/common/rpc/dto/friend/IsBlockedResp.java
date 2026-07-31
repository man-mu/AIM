package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 检查是否被拉黑响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsBlockedResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean isBlocked;
}
