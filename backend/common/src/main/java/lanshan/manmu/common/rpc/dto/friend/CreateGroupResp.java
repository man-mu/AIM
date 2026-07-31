package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 创建好友分组响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private FriendGroupDTO group;
}
