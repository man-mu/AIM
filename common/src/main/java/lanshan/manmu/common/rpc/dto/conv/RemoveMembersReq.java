package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移除会话成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveMembersReq {

    private long conversationId;
    private long operatorId;
    private List<Long> userIds;
}
