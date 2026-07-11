package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取用户信息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetUserInfoResp {

    private List<UserInfo> users;
}
