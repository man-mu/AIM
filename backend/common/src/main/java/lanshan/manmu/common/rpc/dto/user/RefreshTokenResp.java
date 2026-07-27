package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Refresh Token 刷新响应（仅返回新的 accessToken，refreshToken 不变）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResp {

    private String accessToken;
    private long accessExpire;
}