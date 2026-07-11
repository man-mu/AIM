package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标记已读请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadReq {

    private long userId;
    private long conversationId;
    private long lastReadSeq;
}
