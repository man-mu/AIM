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

    // file-service — 文件状态
    public static final int FILE_STATUS_PENDING   = 0;
    public static final int FILE_STATUS_CONFIRMED = 1;
    public static final int FILE_STATUS_DELETED   = 2;

    // file-service — 文件大小限制（按 purpose 区分）
    public static final long FILE_MAX_SIZE_IMAGE      = 50L * 1024 * 1024;   // 图片 ≤ 50MB
    public static final long FILE_MAX_SIZE_ATTACHMENT = 100L * 1024 * 1024;  // 附件 ≤ 100MB

    // file-service — Presigned URL 有效期（服务端固定，忽略客户端传值）
    public static final int FILE_PRESIGN_EXPIRE_SEC = 1800;  // 30 分钟

    // file-service — Zombie 清理
    public static final int  FILE_ZOMBIE_TTL_MINUTES       = 30;       // PENDING 超 30 分钟视为 zombie
    public static final long FILE_ZOMBIE_SCAN_INTERVAL_MS = 300_000L;  // 5 分钟扫一次

    private CommonConst() {}
}
