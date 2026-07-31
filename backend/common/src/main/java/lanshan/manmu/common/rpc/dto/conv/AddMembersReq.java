package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 添加会话成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMembersReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long operatorId;
    private List<Long> userIds;
}
