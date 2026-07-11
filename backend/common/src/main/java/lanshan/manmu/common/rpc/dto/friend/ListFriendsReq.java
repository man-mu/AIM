package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看好友列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFriendsReq {

    private long userId;
    private Long groupId;
    private int pageNum;
    private int pageSize;
}
