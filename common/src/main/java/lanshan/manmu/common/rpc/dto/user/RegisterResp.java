package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResp {

    private long userId;
    private TokenPair tokens;
    private UserInfo user;
}
