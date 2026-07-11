package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看黑名单列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListBlacklistReq {

    private long userId;
    private int pageNum;
    private int pageSize;
}
