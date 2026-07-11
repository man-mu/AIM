package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索用户响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchUsersResp {

    private List<UserInfo> users;
    private long total;
}
