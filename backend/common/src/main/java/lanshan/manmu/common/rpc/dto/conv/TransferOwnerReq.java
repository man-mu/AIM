package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 转让群主请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferOwnerReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long fromUserId;
    private long toUserId;
}
