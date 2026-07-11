package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息撤回事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecalledEvent {

    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
}
