package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Refresh Token 刷新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String refreshToken;
}