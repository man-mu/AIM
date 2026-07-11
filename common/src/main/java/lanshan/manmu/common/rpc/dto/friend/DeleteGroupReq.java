package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除好友分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteGroupReq {

    private long userId;
    private long groupId;
}
