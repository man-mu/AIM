package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 登录响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private TokenPair tokens;
    private UserInfo user;
}
