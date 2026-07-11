package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginReq {

    private String account;
    private String password;
    private String deviceId;
    private String platform;
}
