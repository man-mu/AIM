package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建好友分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupReq {

    private long userId;
    private String name;
    private int sortOrder;
}
