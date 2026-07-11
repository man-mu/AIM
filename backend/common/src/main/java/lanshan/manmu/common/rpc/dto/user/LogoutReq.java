package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登出请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutReq {

    private long userId;
    private String tokenId;
}
