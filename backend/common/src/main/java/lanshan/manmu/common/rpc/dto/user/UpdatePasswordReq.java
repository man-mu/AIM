package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 修改密码请求（独立 RPC，与 updateProfile 解耦）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePasswordReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String oldPassword;
    private String newPassword;
}