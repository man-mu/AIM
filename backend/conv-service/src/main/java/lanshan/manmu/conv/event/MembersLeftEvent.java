package lanshan.manmu.conv.event;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 成员离开会话的 Spring 内部事件（仅在 conv-service 内部使用，不序列化）。
 * <p>由 ConvServiceImpl 在事务内发布，ConvEventListener 在 AFTER_COMMIT 阶段消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembersLeftEvent {
    private long convId;
    private List<Long> userIds;
    private long removedBy;
}
