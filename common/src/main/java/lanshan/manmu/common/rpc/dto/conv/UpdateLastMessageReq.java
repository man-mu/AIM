package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新最后一条消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLastMessageReq {

    private long conversationId;
    private long lastMessageId;
    private long maxSeq;
    private String lastMessagePreview;
}
