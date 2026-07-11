package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterReq {

    private String username;
    private String password;
    private String phone;
    private String email;
    private String deviceId;
    private String platform;
}
