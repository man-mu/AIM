package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看分组列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListGroupsReq {

    private long userId;
}
