package lanshan.manmu.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成员离开会话事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberLeftEvent {

    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_ids")
    private List<Long> userIds;
    @JsonProperty("removed_by")
    private long removedBy;
}
