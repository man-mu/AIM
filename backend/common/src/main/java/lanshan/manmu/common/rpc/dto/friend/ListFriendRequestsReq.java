package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看好友申请列表请求（契约 §4）。
 * <p>{@code direction}：'incoming' = 收到的申请（仅 status=1 待处理），'outgoing' = 发出的申请（全状态）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFriendRequestsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String direction;
    private int pageNum;
    private int pageSize;
}
