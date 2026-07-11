package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息编辑事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditedEvent {

    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("new_content")
    private Map<String, Object> newContent;
}
