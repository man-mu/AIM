package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 验证请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenReq {

    private String accessToken;
}
