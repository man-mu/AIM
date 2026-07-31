package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 更新会话设置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long conversationId;
    private Boolean isMuted;
    private Boolean isPinned;
    private String nickname;
}
