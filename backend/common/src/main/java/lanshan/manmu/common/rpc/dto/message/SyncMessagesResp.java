package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMessagesResp {

    private List<MessageDTO> messages;
    private boolean hasMore;
    private long maxSeq;
}
