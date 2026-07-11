package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取会话设置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSettingsReq {

    private long userId;
    private long conversationId;
}
