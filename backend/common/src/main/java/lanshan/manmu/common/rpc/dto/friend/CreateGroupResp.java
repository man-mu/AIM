package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建好友分组响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupResp {

    private FriendGroupDTO group;
}
