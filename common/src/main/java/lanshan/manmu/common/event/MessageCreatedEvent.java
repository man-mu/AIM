package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息创建事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreatedEvent {

    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("sender_id")
    private long senderId;
    @JsonProperty("sender_type")
    private String senderType;
    @JsonProperty("msg_type")
    private int msgType;
    @JsonProperty("content")
    private Map<String, Object> content;
    private long seq;
    @JsonProperty("reply_to_msg_id")
    private long replyToMsgId;
    @JsonProperty("created_at")
    private long createdAt;
}
