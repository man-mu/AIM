package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {

    private long userId;
    private TokenPair tokens;
    private UserInfo user;
}
