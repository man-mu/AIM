package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Token 验证请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accessToken;
}
