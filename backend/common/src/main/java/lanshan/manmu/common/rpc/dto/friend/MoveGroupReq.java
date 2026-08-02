package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 移动好友到分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveGroupReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long friendUserId;
    private long groupId;
}
