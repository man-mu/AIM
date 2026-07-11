package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetMessagesReq {

    private List<Long> messageIds;
}
