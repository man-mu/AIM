package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取用户信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserInfoReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
}
