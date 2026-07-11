package lanshan.manmu.common.constant;

/**
 * WebSocket 事件类型常量。
 */
public final class WsEvent {

    // 客户端 → 服务端
    public static final String PING                = "ping";
    public static final String SUBSCRIBE_PRESENCE  = "subscribe_presence";
    public static final String UNSUBSCRIBE_PRESENCE = "unsubscribe_presence";
    public static final String TYPING              = "typing";
    public static final String TYPING_STOP         = "typing_stop";
    public static final String ACK                 = "ack";

    // 服务端 → 客户端
    public static final String PONG             = "pong";
    public static final String MESSAGE_NEW      = "message.new";
    public static final String MESSAGE_RECALLED  = "message.recalled";
    public static final String MESSAGE_EDITED    = "message.edited";
    public static final String PRESENCE          = "presence";
    public static final String READ_SYNC         = "read_sync";
    public static final String TYPING_NOTIFY     = "typing";
    public static final String TYPING_STOP_NOTIFY = "typing.stop";
    public static final String UNREAD_COUNT     = "unread_count";
    public static final String READ_RECEIPT      = "read_receipt";

    private WsEvent() {}
}
