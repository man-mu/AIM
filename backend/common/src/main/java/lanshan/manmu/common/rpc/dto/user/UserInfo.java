package lanshan.manmu.common.rpc.dto.user;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String username;
    private String phone;
    private String email;
    private String avatar;
    private int gender;
    private String bio;
    private long birthday;
    private long createdAt;
    private long updatedAt;
    private BigDecimal balance;
}
