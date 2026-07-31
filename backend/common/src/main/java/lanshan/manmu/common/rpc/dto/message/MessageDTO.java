package lanshan.manmu.common.rpc.dto.message;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 消息 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long messageId;
    private long conversationId;
    private long seq;
    private long fromUserId;
    private int msgType;
    private int status;
    private Map<String, Object> content;
    private long replyToId;
    private long replyToMessageId;
    private String replyToPreview;
    private int editCount;
    private long editedAt;
    private long createdAt;
}
