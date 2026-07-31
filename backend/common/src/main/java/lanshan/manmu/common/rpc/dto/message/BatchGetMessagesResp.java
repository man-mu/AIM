package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 批量获取消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetMessagesResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<MessageDTO> messages;
}
