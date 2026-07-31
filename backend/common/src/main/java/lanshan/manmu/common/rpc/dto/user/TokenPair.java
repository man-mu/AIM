package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Token 对（Access + Refresh）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String refreshToken;
    private long accessExpire;
    private long refreshExpire;
}
