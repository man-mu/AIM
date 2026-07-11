package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息删除事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeletedEvent {

    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("delete_for_all")
    private boolean deleteForAll;
}
