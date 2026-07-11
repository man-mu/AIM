package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤回消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallMessageReq {

    private long messageId;
    private long conversationId;
    private long userId;
}
