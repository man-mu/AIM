package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

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
    private double balance;
}
