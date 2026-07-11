package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取会话设置响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSettingsResp {

    private boolean isMuted;
    private boolean isPinned;
    private String nickname;
}
