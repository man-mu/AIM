package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResp {

    private long messageId;
    private long seq;
    private long createdAt;
}
