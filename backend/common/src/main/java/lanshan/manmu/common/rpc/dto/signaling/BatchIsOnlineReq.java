package lanshan.manmu.common.rpc.dto.signaling;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量检查在线状态请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchIsOnlineReq {

    private List<Long> userIds;
}
