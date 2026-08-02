package lanshan.manmu.common.rpc.dto.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看好友列表响应（契约 §4 分页壳 {@code {list, total, pageNum, pageSize}}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFriendsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<FriendInfoDTO> list;
    private long total;
    private int pageNum;
    private int pageSize;
}
