package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索用户请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchUsersReq {

    private String keyword;
    private int pageNum;
    private int pageSize;
}
