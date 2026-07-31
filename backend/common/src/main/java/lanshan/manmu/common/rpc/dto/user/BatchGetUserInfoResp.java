package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 批量获取用户信息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetUserInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<UserInfo> users;
}
