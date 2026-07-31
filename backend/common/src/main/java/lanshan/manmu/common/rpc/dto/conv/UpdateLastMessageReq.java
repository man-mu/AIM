package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 更新最后一条消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLastMessageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long lastMessageId;
    private long maxSeq;
    private String lastMessagePreview;
}
