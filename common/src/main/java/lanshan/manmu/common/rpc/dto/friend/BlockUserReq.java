package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拉黑用户请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockUserReq {

    private long userId;
    private long targetUserId;
}
