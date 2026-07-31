package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 撤回消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallMessageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long messageId;
    private long conversationId;
    private long userId;
}
