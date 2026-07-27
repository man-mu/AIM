package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登出请求（同时吊销 accessToken + refreshToken，不向后兼容）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutReq {

    private String accessToken;
    private String refreshToken;
}
