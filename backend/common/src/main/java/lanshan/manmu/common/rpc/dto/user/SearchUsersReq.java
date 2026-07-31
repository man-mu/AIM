package lanshan.manmu.common.rpc.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 搜索用户请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchUsersReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String keyword;
    private int pageNum;
    private int pageSize;
}
