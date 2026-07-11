package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转让群主请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferOwnerReq {

    private long conversationId;
    private long fromUserId;
    private long toUserId;
}
