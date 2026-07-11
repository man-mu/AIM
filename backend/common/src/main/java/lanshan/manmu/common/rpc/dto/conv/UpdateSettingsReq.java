package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新会话设置请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsReq {

    private long userId;
    private long conversationId;
    private Boolean isMuted;
    private Boolean isPinned;
    private String nickname;
}
