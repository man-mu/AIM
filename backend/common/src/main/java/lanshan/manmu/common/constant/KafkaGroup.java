package lanshan.manmu.common.constant;

/**
 * Kafka Consumer Group 常量。
 */
public final class KafkaGroup {

    /** message-service 自消费写 inbox */
    public static final String MESSAGE_SERVICE = "message-service";
    /** signaling-service 消费全量事件做扇出 */
    public static final String SIGNALING_SERVICE = "signaling-service";
    /** conv-service 消费 message.created 更新 last_message */
    public static final String CONV_SERVICE = "conv-service";

    private KafkaGroup() {}
}
