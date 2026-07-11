package lanshan.manmu.common.rpc.dto.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看黑名单列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListBlacklistResp {

    private List<FriendInfoDTO> users;
    private long total;
}
