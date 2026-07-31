package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 同步消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMessagesReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long userId;
    private long fromSeq;
    private int limit;
}
