package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMessagesReq {

    private long conversationId;
    private long userId;
    private long fromSeq;
    private int limit;
}
