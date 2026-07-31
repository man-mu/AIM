package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送消息前置校验请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreCheckSendReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long userId;
}
