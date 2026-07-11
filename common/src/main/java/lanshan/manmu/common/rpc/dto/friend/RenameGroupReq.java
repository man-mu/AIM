package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重命名好友分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameGroupReq {

    private long userId;
    private long groupId;
    private String name;
}
