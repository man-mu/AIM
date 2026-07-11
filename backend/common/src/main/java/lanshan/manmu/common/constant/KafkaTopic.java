package lanshan.manmu.common.constant;

/**
 * Kafka Topic 常量。
 */
public final class KafkaTopic {

    public static final String MESSAGE_CREATED           = "message.created";
    public static final String MESSAGE_RECALLED          = "message.recalled";
    public static final String MESSAGE_EDITED            = "message.edited";
    public static final String MESSAGE_DELETED           = "message.deleted";
    public static final String CONVERSATION_READ_UPDATED = "conversation.read.updated";
    public static final String CONVERSATION_BOT_ADDED    = "conversation.bot.added";
    public static final String CONVERSATION_BOT_REMOVED  = "conversation.bot.removed";
    public static final String CONVERSATION_MEMBER_JOINED = "conversation.member.joined";
    public static final String CONVERSATION_MEMBER_LEFT  = "conversation.member.left";
    public static final String BOT_EVENT_AI              = "bot.event.ai";
    public static final String MESSAGE_CREATED_DLQ       = "message.created.dlq";

    private KafkaTopic() {}
}
