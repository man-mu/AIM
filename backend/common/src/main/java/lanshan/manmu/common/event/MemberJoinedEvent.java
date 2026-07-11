package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成员加入会话事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberJoinedEvent {

    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_ids")
    private List<Long> userIds;
    @JsonProperty("joined_by")
    private long joinedBy;
}
