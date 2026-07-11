package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 验证响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResp {

    private boolean valid;
    private long userId;
    private String deviceId;
    private long expiresAt;
}
