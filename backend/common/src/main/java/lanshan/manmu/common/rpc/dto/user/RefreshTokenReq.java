package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Refresh Token 刷新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenReq {

    private String refreshToken;
}