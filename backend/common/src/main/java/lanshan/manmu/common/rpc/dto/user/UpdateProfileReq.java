package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新资料请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileReq {

    private long userId;
    private String avatar;
    private Integer gender;
    private String bio;
    private Long birthday;
    private String oldPassword;
    private String newPassword;
    private String phone;
    private String email;
}
