package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除好友请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFriendReq {

    private long userId;
    private long friendUserId;
}
