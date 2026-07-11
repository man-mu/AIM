package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看会话成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMembersReq {

    private long conversationId;
    private long userId;
    private int pageNum;
    private int pageSize;
}
