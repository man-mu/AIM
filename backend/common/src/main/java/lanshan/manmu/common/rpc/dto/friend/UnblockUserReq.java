package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 取消拉黑用户请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnblockUserReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long targetUserId;
}
