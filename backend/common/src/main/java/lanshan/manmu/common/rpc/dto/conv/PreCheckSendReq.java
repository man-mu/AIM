package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息前置校验请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreCheckSendReq {

    private long conversationId;
    private long userId;
}
