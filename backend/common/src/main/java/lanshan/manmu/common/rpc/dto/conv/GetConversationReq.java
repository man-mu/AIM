package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取会话请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetConversationReq {

    private long conversationId;
    private long userId;
}
