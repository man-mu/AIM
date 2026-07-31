package lanshan.manmu.conv.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * markRead 完成的 Spring 内部事件（仅在 conv-service 内部使用，不序列化）。
 * <p>由 ConvServiceImpl 在事务内发布，ConvEventListener 在 AFTER_COMMIT 阶段消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadCompletedEvent {
    private long userId;
    private long convId;
    private long lastReadSeq;
}
