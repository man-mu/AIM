package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话已读更新事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationReadUpdatedEvent {

    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("last_read_seq")
    private long lastReadSeq;
}
