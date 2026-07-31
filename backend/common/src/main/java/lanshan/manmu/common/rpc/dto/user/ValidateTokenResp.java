package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Token 验证响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean valid;
    private long userId;
    private long expiresAt;
}
