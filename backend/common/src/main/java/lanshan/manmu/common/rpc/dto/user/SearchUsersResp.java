package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 搜索用户响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchUsersResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<UserInfo> users;
    private long total;
}
