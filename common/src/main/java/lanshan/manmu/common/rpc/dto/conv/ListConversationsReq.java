package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看会话列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListConversationsReq {

    private long userId;
    private int pageNum;
    private int pageSize;
}
