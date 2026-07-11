package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取用户信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserInfoReq {

    private long userId;
}
