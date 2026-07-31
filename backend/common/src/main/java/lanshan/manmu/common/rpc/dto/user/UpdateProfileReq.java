package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 更新资料请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String avatar;
    private Integer gender;
    private String bio;
    private Long birthday;
    private String phone;
    private String email;
}
