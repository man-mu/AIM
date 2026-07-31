package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 注册请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String phone;
    private String email;
    private String deviceId;
    private String platform;
}
