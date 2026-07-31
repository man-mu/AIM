package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看好友申请列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFriendRequestsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private int pageNum;
    private int pageSize;
}
