package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取会话设置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSettingsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long conversationId;
}
