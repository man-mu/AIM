package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取用户信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetUserInfoReq {

    private List<Long> userIds;
}
