package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取会话设置响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSettingsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean isMuted;
    private boolean isPinned;
    private String nickname;
}
