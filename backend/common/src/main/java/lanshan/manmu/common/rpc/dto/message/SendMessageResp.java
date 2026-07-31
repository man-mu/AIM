package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long messageId;
    private long seq;
    private long createdAt;
}
