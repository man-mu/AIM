package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMessageReq {

    private long messageId;
    private long conversationId;
    private long userId;
    private boolean deleteForAll;
}
