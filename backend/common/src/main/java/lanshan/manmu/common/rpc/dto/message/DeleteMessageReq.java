package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 删除消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMessageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long messageId;
    private long conversationId;
    private long userId;
    private boolean deleteForAll;
}
