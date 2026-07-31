package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 标记已读请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long conversationId;
    private long lastReadSeq;
}
