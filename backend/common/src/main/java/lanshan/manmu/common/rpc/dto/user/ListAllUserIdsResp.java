package lanshan.manmu.common.rpc.dto.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列出全部用户 ID 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListAllUserIdsResp {

    private List<Long> userIds;
}
