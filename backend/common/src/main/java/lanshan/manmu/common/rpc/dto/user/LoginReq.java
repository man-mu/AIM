package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 登录请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String account;
    private String password;
    private String deviceId;
    private String platform;
}
