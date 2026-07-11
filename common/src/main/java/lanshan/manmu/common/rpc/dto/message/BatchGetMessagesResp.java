package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetMessagesResp {

    private List<MessageDTO> messages;
}
