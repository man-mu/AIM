package lanshan.manmu.common.constant;

import java.time.Duration;

/**
 * 通用常量。
 */
public final class CommonConst {

    // 消息管理
    public static final int RECALL_WINDOW_SEC  = 120;
    public static final int EDIT_WINDOW_SEC    = 120;

    // 群组限制
    public static final int MAX_MEMBER_COUNT     = 500;
    public static final int MAX_GROUP_NAME_LEN   = 64;
    public static final int MAX_ALIAS_LEN        = 32;
    public static final int MAX_ANNOUNCEMENT_LEN = 1024;
    public static final long MAX_FILE_SIZE       = 100L * 1024 * 1024;

    // 幂等
    public static final Duration MSG_IDEMPOTENT_TTL = Duration.ofHours(2);

    // JWT
    public static final long JWT_DEFAULT_EXPIRE_SEC  = 7200;
    public static final long JWT_DEFAULT_REFRESH_SEC = 604800;

    // Redis Key 模板
    public static final String REDIS_KEY_SESSION     = "session:%d:%s";
    public static final String REDIS_KEY_MSG_IDEMP   = "msg:idempotent:%s";
    public static final String REDIS_KEY_WS_ONLINE   = "ws:online";
    public static final String REDIS_KEY_WS_USER     = "ws:user:%d:%s";
    public static final String REDIS_KEY_UNREAD      = "unread:%d:%d";

    // Outbox
    public static final int OUTBOX_STATUS_PENDING = 0;
    public static final int OUTBOX_STATUS_SENT    = 1;
    public static final int OUTBOX_STATUS_FAILED  = 2;
    public static final int OUTBOX_DEFAULT_MAX_RETRIES = 10;
    public static final int OUTBOX_BATCH_SIZE     = 100;

    private CommonConst() {}
}
