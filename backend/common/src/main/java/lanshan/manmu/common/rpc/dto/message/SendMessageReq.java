package lanshan.manmu.common.rpc.dto.message;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long fromUserId;
    private int msgType;
    private Map<String, Object> content;
    private Long replyToId;
    private String clientMsgId;
}
