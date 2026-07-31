package lanshan.manmu.common.rpc.dto.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看好友申请列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFriendRequestsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<FriendRequestDTO> requests;
    private long total;
}
