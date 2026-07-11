# L0 — common 工程地基（详细计划）

> **目标**：一次定义好全部 Phase 1 的 Dubbo 接口契约、事件 DTO、常量、工具类，使 L1~L5 各服务可直接引用编译。
> **学习要点**：统一响应模式 · 异常体系设计 · Snowflake 分布式 ID · Kafka 事件契约 · Dubbo 接口先行
> **验证**：`mvn compile -pl common` 成功 + SnowflakeIdWorker 单测通过

---

## 构建顺序总览

```
Step 1  result/        → Result<T> + PageResult<T>
Step 2  exception/     → ErrorCode + BizException
Step 3  constant/      → KafkaTopic / KafkaGroup / MsgType / MsgStatus / ConvType / MemberRole / WsEvent / CommonConst
Step 4  event/         → 9 个 Kafka 事件 DTO
Step 5  util/          → SnowflakeIdWorker
Step 6  rpc/dto/       → 全部 Request/Response DTO（按服务分组）
Step 7  rpc/           → 7 个 Dubbo 服务接口
Step 8  验证           → mvn compile + 单测
```

---

## Step 1 — result/（统一响应）

### `result/Result.java`

```java
package lanshan.manmu.common.result;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    /** 业务码：0=成功，非0=失败 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 数据载荷 */
    private T data;

    public static <T> Result<T> ok(T data)             { return new Result<>(0, "success", data); }
    public static <T> Result<T> ok()                    { return new Result<>(0, "success", null); }
    public static <T> Result<T> fail(int code, String msg) { return new Result<>(code, msg, null); }
    public static <T> Result<T> fail(ErrorCode ec)     { return new Result<>(ec.getCode(), ec.getMessage(), null); }

    public boolean isSuccess() { return code == 0; }
}
```

**学习点**：为什么用泛型 `Result<T>` 而不是 `Result` + Object？ляться 编译期类型安全，Dubbo Hessian2 序列化也能正确还原泛型类型。

### `result/PageResult.java`

```java
package lanshan.manmu.common.result;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }
}
```

### `result/CursorResult.java`

```java
package lanshan.manmu.common.result;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorResult<T> {
    private List<T> list;
    private String nextCursor;
    private boolean hasMore;
    private long total;

    public static <T> CursorResult<T> of(List<T> list, String nextCursor, boolean hasMore, long total) {
        return new CursorResult<>(list, nextCursor, hasMore, total);
    }
}
```

**学习点**：IM 消息列表用游标分页（ 基于 seq 或 messageId）比传统分页更合适——消息是追加写的，传统分页在新增数据后会"跳页"。

---

## Step 2 — exception/（异常体系）

### `exception/ErrorCode.java`

错误码按服务分段，百位段区分服务：

```java
package lanshan.manmu.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 通用
    SUCCESS           (0,    "success"),
    BAD_REQUEST       (400,  "请求参数错误"),
    UNAUTHORIZED      (401,  "未认证"),
    FORBIDDEN         (403,  "无权限"),
    NOT_FOUND         (404,  "资源不存在"),
    INTERNAL_ERROR    (500,  "服务器内部错误"),

    // user-service 1xxxx
    USER_NOT_FOUND        (10001, "用户不存在"),
    USER_ALREADY_EXISTS   (10002, "用户名已存在"),
    USER_PHONE_EXISTS     (10003, "手机号已被注册"),
    USER_PASSWORD_ERROR   (10004, "密码错误"),
    USER_TOKEN_INVALID    (10005, "Token 无效或已过期"),
    USER_TOKEN_EXPIRED    (10006, "Token 已过期"),
    USER_SESSION_NOT_FOUND(10007, "会话不存在"),
    USER_FORBIDDEN        (10008, "用户被禁用"),

    // friend-service 2xxxx
    FRIEND_ALREADY_EXISTS    (20001, "已经是好友"),
    FRIEND_NOT_EXISTS        (20002, "非好友关系"),
    FRIEND_REQUEST_EXISTS    (20003, "好友申请已存在"),
    FRIEND_REQUEST_NOT_FOUND(20004, "好友申请不存在"),
    FRIEND_REQUEST_HANDLED   (20005, "申请已处理"),
    BLOCKED_BY_USER          (20006, "已被对方拉黑"),
    ALREADY_BLOCKED          (20007, "已拉黑该用户"),
    NOT_BLOCKED             (20008, "未拉黑该用户"),

    // conv-service 3xxxx
    CONV_NOT_FOUND        (30001, "会话不存在"),
    CONV_MEMBER_EXISTS    (30002, "用户已是会话成员"),
    CONV_MEMBER_NOT_FOUND (30003, "用户不在会话中"),
    CONV_NOT_MEMBER       (30004, "非会话成员"),
    CONV_PERMISSION_DENIED(30005, "权限不足"),
    CONV_MUTED            (30006, "已被禁言"),
    CONV_MUTED_ALL        (30007, "全员禁言中"),
    CONV_MEMBER_LIMIT     (30008, "成员数超上限"),
    CONV_OWNER_TRANSFER_SELF(30009, "不能转让给自己"),

    // message-service 4xxxx
    MESSAGE_NOT_FOUND      (40001, "消息不存在"),
    MESSAGE_RECALL_TIMEOUT (40002, "撤回超时"),
    MESSAGE_EDIT_TIMEOUT  (40003, "编辑超时"),
    MESSAGE_DUPLICATE      (40004, "重复消息"),
    MESSAGE_NOT_SENDER     (40005, "非消息发送者"),
    MESSAGE_ALREADY_RECALLED(40006, "消息已撤回"),
    MESSAGE_SEQ_GEN_FAILED (40007, "序号生成失败"),

    // file-service 5xxxx
    FILE_NOT_FOUND        (50001, "文件不存在"),
    FILE_UPLOAD_FAILED    (50002, "文件上传失败"),
    FILE_TOO_LARGE        (50003, "文件过大"),
    FILE_TYPE_NOT_SUPPORT (50004, "不支持的文件类型"),

    // signaling-service 6xxxx
    NOTIF_NOT_FOUND       (60001, "通知不存在"),
    ;

    private final int code;
    private final String message;
}
```

**学习点**：错误码分段是微服务约定的一种"契约文档"——看到 30xxx 就知道是 conv-service 报的。枚举比常量类好在：IDE 自动补全 + 可遍历 + 不会写重复值。

### `exception/BizException.java`

```java
package lanshan.manmu.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

**学习点**：继承 `RuntimeException`（非受检异常）而非 `Exception`——微服务里业务异常不该被 `try-catch` 吞掉，应该直接向上抛到 Dubbo 框架层统一处理。Dubbo 会自动将异常序列化传输给消费端。

---

## Step 3 — constant/（常量）

### `constant/KafkaTopic.java`

```java
package lanshan.manmu.common.constant;

public final class KafkaTopic {
    public static final String MESSAGE_CREATED           = "message.created";
    public static final String MESSAGE_RECALLED          = "message.recalled";
    public static final String MESSAGE_EDITED            = "message.edited";
    public static final String MESSAGE_DELETED           = "message.deleted";
    public static final String CONVERSATION_READ_UPDATED = "conversation.read.updated";
    public static final String CONVERSATION_BOT_ADDED    = "conversation.bot.added";
    public static final String CONVERSATION_BOT_REMOVED = "conversation.bot.removed";
    public static final String CONVERSATION_MEMBER_JOINED = "conversation.member.joined";
    public static final String CONVERSATION_MEMBER_LEFT  = "conversation.member.left";
    public static final String BOT_EVENT_AI              = "bot.event.ai";
    public static final String MESSAGE_CREATED_DLQ       = "message.created.dlq";

    private KafkaTopic() {}
}
```

**学习点**：Topic 名用 `服务域.动作` 的点分命名法——一眼看出谁产生的、发生了什么。Kafka 路由按 Topic，消费者订阅 Topic 就能拿到这个事件流。

### `constant/KafkaGroup.java`

```java
package lanshan.manmu.common.constant;

public final class KafkaGroup {
    /** message-service 自消费写 inbox */
    public static final String MESSAGE_SERVICE = "message-service";
    /** signaling-service 消费全量事件做扇出 */
    public static final String SIGNALING_SERVICE = "signaling-service";
    /** conv-service 消费 message.created 更新 last_message */
    public static final String CONV_SERVICE = "conv-service";

    private KafkaGroup() {}
}
```

**学习点**：Consumer Group 是 Kafka 的核心概念——同一个 Group 内的消费者**分担**消费（每分区只被一个消费者消费），不同 Group 各自独立消费全量。signaling 和 conv 都消费 `message.created`，但因为 Group 不同所以各拿一份全量。

### `constant/MsgType.java`

```java
package lanshan.manmu.common.constant;

public final class MsgType {
    public static final int UNSPECIFIED = 0;
    public static final int TEXT   = 1;
    public static final int IMAGE  = 2;
    public static final int FILE   = 3;
    public static final int VIDEO  = 4;
    public static final int AUDIO  = 5;
    public static final int LOCATION = 6;
    public static final int SYSTEM = 7;
    public static final int CUSTOM = 8;
    public static final int BOT    = 9;

    private MsgType() {}
}
```

### `constant/MsgStatus.java`

```java
package lanshan.manmu.common.constant;

public final class MsgStatus {
    public static final int UNSPECIFIED = 0;
    public static final int NORMAL    = 1;
    public static final int RECALLED  = 2;
    public static final int EDITED    = 3;
    public static final int STREAMING = 4;  // AI 流式中

    private MsgStatus() {}
}
```

### `constant/ConvType.java`

```java
package lanshan.manmu.common.constant;

public final class ConvType {
    public static final int UNSPECIFIED = 0;
    public static final int SINGLE = 1;  // 单聊
    public static final int GROUP  = 2;  // 群聊

    private ConvType() {}
}
```

### `constant/MemberRole.java`

```java
package lanshan.manmu.common.constant;

public final class MemberRole {
    public static final int UNSPECIFIED = 0;
    public static final int OWNER  = 1;
    public static final int ADMIN  = 2;
    public static final int MEMBER = 3;

    private MemberRole() {}
}
```

### `constant/WsEvent.java`

WebSocket 事件类型常量（L4 ws-gateway 用）：

```java
package lanshan.manmu.common.constant;

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
```

**学习点**：WS 事件定义是前后端实时通信的"协议"——客户端发什么 event name、服务端推什么 event name，必须提前约定。这里 `TYPING` 和 `TYPING_NOTIFY` 名称不同是故意区分方向的（客户端发 `typing`，服务端推给其他人是 `typing`）。

### `constant/CommonConst.java`

```java
package lanshan.manmu.common.constant;

import java.time.Duration;

public final class CommonConst {
    // 消息管理
    public static final int RECALL_WINDOW_SEC  = 120;  // 撤回时间窗口 2 分钟
    public static final int EDIT_WINDOW_SEC    = 120;  // 编辑时间窗口 2 分钟

    // 群组限制
    public static final int MAX_MEMBER_COUNT     = 500;
    public static final int MAX_GROUP_NAME_LEN   = 64;
    public static final int MAX_ALIAS_LEN        = 32;
    public static final int MAX_ANNOUNCEMENT_LEN = 1024;
    public static final long MAX_FILE_SIZE       = 100L * 1024 * 1024;  // 100MB

    // 幂等
    public static final Duration MSG_IDEMPOTENT_TTL = Duration.ofHours(2);

    // JWT
    public static final long JWT_DEFAULT_EXPIRE_SEC  = 7200;       // 2h
    public static final long JWT_DEFAULT_REFRESH_SEC = 604800;     // 7d

    // Redis Key 模板
    public static final String REDIS_KEY_SESSION     = "session:%d:%s";        // userId, deviceId
    public static final String REDIS_KEY_MSG_IDEMP   = "msg:idempotent:%s";    // clientMsgId
    public static final String REDIS_KEY_WS_ONLINE   = "ws:online";            // Set: 在线用户ID
    public static final String REDIS_KEY_WS_USER     = "ws:user:%d:%s";        // userId, deviceId → serverId
    public static final String REDIS_KEY_UNREAD      = "unread:%d:%d";         // userId, convId → count

    // Outbox
    public static final int OUTBOX_STATUS_PENDING = 0;
    public static final int OUTBOX_STATUS_SENT    = 1;
    public static final int OUTBOX_STATUS_FAILED  = 2;
    public static final int OUTBOX_DEFAULT_MAX_RETRIES = 10;
    public static final int OUTBOX_BATCH_SIZE     = 100;

    private CommonConst() {}
}
```

---

## Step 4 — event/（Kafka 事件 DTO）

> 所有事件 DTO 用 FastJSON2 序列化。`@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` 是 Dubbo Hessian2 反序列化的最低要求。
> JSON 字段名用 **snake_case**（与 MAIM Go 结构体 json tag 一致），通过 `@JsonProperty` 映射。

### `event/MessageCreatedEvent.java`

```java
package lanshan.manmu.common.event;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageCreatedEvent {
    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("sender_id")
    private long senderId;
    @JsonProperty("sender_type")
    private String senderType;     // "user" / "bot"
    @JsonProperty("msg_type")
    private int msgType;
    @JsonProperty("content")
    private Map<String, Object> content;  // JSONB 原始内容
    private long seq;
    @JsonProperty("reply_to_msg_id")
    private long replyToMsgId;
    @JsonProperty("created_at")
    private long createdAt;       // epoch millis
}
```

**学习点**：`content` 用 `Map<String, Object>` 而非具体类型——因为消息内容是 JSONB，结构因 `msgType` 而异（文本有 `text` 字段、图片有 `file_id/url/width/height` 字段）。Go 侧也是 `map[string]any`。这是"Schema-on-Read"模式，消费端按 msgType 自己解释。

### `event/MessageRecalledEvent.java`

```java
package lanshan.manmu.common.event;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecalledEvent {
    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
}
```

### `event/MessageEditedEvent.java`

```java
package lanshan.manmu.common.event;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditedEvent {
    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("new_content")
    private Map<String, Object> newContent;
}
```

### `event/MessageDeletedEvent.java`

```java
package lanshan.manmu.common.event;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeletedEvent {
    @JsonProperty("message_id")
    private long messageId;
    @JsonProperty("conv_id")
    private long convId;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("delete_for_all")
    private boolean deleteForAll;
}
```

### `event/ConversationReadUpdatedEvent.java`

```java
package lanshan.manmu.common.event;

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
```

### `event/MemberJoinedEvent.java`

```java
package lanshan.manmu.common.event;

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
```

### `event/MemberLeftEvent.java`

```java
package lanshan.manmu.common.event;

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
```

### `event/BotAddedToConvEvent.java` / `event/BotRemovedFromConvEvent.java`

Phase 1 不实现 Bot 功能，**跳过**。L0 只定义 Phase 1 运行链路所需的事件。

---

## Step 5 — util/（工具类）

### `util/SnowflakeIdWorker.java`

移植自 MAIM `pkg/snowflake`（底层是 `bwmarrin/snowflake`），Java 侧自行实现位运算：

```java
package lanshan.manmu.common.util;

/**
 * Snowflake 分布式 ID 生成器。
 *
 * 位布局（64 bit）:
 *   1 bit      : 符号位（0）
 *   41 bit     : 时间戳（毫秒，自 Epoch 起算），可用 ~69 年
 *   10 bit     : workerId（0~1023），区分不同服务实例
 *   12 bit     : 序列号（0~4095），同一毫秒内递增
 *
 * Epoch = 2024-01-01 00:00:00 UTC (自定义), 不用 Twitter 原值
 */
public class SnowflakeIdWorker {

    /** 起始时间戳：2024-01-01 00:00:00 UTC (毫秒) */
    private static final long EPOCH = 1704067200000L;

    /** workerId 占用位数 */
    private static final long WORKER_ID_BITS = 10L;
    /** 序列号占用位数 */
    private static final long SEQUENCE_BITS  = 12L;

    /** workerId 最大值 = 1023 */
    private static final long MAX_WORKER_ID  = ~(-1L << WORKER_ID_BITS);  // 1023
    /** 序列号最大值 = 4095 */
    private static final long SEQUENCE_MASK  = ~(-1L << SEQUENCE_BITS);    // 4095

    /** workerId 左移位数 = 12 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 时间戳左移位数 = 12 + 10 = 22 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * @param workerId 工作节点 ID (0~1023)，不同服务实例必须不同
     */
    public SnowflakeIdWorker(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID)
            throw new IllegalArgumentException(
                "workerId must be in [0, " + MAX_WORKER_ID + "], got " + workerId);
        this.workerId = workerId;
    }

    /** 生成下一个 ID（线程安全） */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (timestamp < lastTimestamp)
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "
                + (lastTimestamp - timestamp) + "ms");

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0)  // 同毫秒内序列耗尽，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
             | (workerId << WORKER_ID_SHIFT)
             | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp)
            timestamp = System.currentTimeMillis();
        return timestamp;
    }

    /** 从 ID 中提取时间戳（毫秒，自 Epoch 起） */
    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /** 从 ID 中提取 workerId */
    public static long extractWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /** 从 ID 中提取序列号 */
    public static long extractSequence(long id) {
        return id & SEQUENCE_MASK;
    }
}
```

**学习点**：
- **为什么 IM 需要 Snowflake 而不是自增 ID？** 微服务多实例并发写同一个表，DB 自增 ID 要加锁竞争，Snowflake 本地生成无锁、趋势递增、ID 含时间信息。
- **时钟回拨问题**：同一毫秒用 seq 消化，不同毫秒重置 seq——seq 12 位支持 4096/ms，单实例峰值约 400 万/秒。
- **workerId 分配约定**（各服务 `application.yml` `snowflake.worker-id` 配置）：

| workerId | 服务 | 备注 |
|---|---|---|
| 0 | user-service | |
| 1 | message-service | 消息量大，优先分配 |
| 2 | conv-service | |
| 3 | friend-service | |
| 4 | file-service | |
| 5 | bot-platform | Phase 2 |
| 6 | ai-bot-service | Phase 2 |
| 7 | signaling-service | |
| 8 | 待预留 | |

> **workerId bean 配置方式**：每个服务在 `config/` 下建 `SnowflakeConfig.java`，从 yml 读 worker-id 注入 `SnowflakeIdWorker`。

---

## Step 6 — rpc/dto/（Request / Response DTO）

> 包路径：`lanshan.manmu.common.rpc.dto`
> 所有 DTO 遵循：`@Data @NoArgsConstructor @AllArgsConstructor` + Lombok Builder 由各服务自行引入
> 字段命名用 **camelCase**（Java 规范），不做 `@JsonProperty`（这些 DTO 只经 Dubbo Hessian2 序列化，不对外暴露 JSON）

### 6.1 user-service DTO

```
rpc/dto/user/
├── RegisterReq.java
├── RegisterResp.java
├── LoginReq.java
├── LoginResp.java
├── TokenPair.java
├── UserInfo.java
├── ValidateTokenReq.java
├── ValidateTokenResp.java
├── UpdateProfileReq.java
├── BatchGetUserInfoReq.java
├── BatchGetUserInfoResp.java
├── SearchUsersReq.java
├── SearchUsersResp.java
├── ListAllUserIdsResp.java
├── LogoutReq.java
└── GetUserInfoReq.java
```

**字段明细**（参考 MAIM proto，Java camelCase）：

#### `RegisterReq`
```java
String username;     String password;     String phone;
String email;        String deviceId;     String platform;   // ios/android/web
```

#### `RegisterResp`
```java
long userId;         TokenPair tokens;    UserInfo user;
```

#### `LoginReq`
```java
String account;      String password;     String deviceId;    String platform;
```

#### `LoginResp`
```java
long userId;         TokenPair tokens;    UserInfo user;
```

#### `TokenPair`
```java
String accessToken;  String refreshToken;
long   accessExpire;  long refreshExpire;   // epoch millis
```

#### `UserInfo`
```java
long   id;           String username;   String phone;
String email;        String avatar;     int    gender;    // 0=unknown 1=male 2=female
String bio;          long   birthday;   long   createdAt;
long   updatedAt;    double balance;
```

#### `ValidateTokenReq`
```java
String accessToken;
```

#### `ValidateTokenResp`
```java
boolean valid;       long userId;        String deviceId;    long expiresAt;
```

#### `LogoutReq`
```java
long userId;         String tokenId;
```

#### `UpdateProfileReq`
```java
long userId;         String avatar;      Integer gender;    String bio;
Long birthday;        String oldPassword; String newPassword;
String phone;         String email;
```

#### `GetUserInfoReq`
```java
long userId;
```

#### `BatchGetUserInfoReq`
```java
List<Long> userIds;
```

#### `BatchGetUserInfoResp`
```java
List<UserInfo> users;
```

#### `SearchUsersReq`
```java
String keyword;      int pageNum;        int pageSize;
```

#### `SearchUsersResp`
```java
List<UserInfo> users;  long total;
```

#### `ListAllUserIdsResp`
```java
List<Long> userIds;
```

**简化说明**：MAIM 原版有 `GetSessions/RevokeSession/GetSettings/UpdateSettings/Recharge/DeductBalance/GetBalance/OAuthLogin/RefreshToken/BindPhone/BindEmail/UpdatePassword` 等方法。Phase 1 学习导向下先砍掉 OAuth/Session管理/余额/设置，保留核心的注册/登录/登出/鉴权/资料/批量查询。**但是接口定义在上面会标注哪些是 Phase 1 实现，哪些是预留。**

---

### 6.2 file-service DTO

```
rpc/dto/file/
├── GetUploadURLReq.java
├── GetUploadURLResp.java
├── ConfirmUploadReq.java
├── ConfirmUploadResp.java
├── GetDownloadURLReq.java
├── GetDownloadURLResp.java
├── GetFileInfoReq.java
├── GetFileInfoResp.java
├── BatchGetFileInfoReq.java
├── BatchGetFileInfoResp.java
├── DeleteFileReq.java
├── FileInfo.java
└── UploadAvatarReq.java
├── UploadAvatarResp.java
```

**字段明细**：

#### `GetUploadURLReq`
```java
String name;         String mimeType;    long size;
long uploaderId;     int purpose;        // FilePurpose: 1=MESSAGE 2=AVATAR 3=DOCUMENT 4=MEDIA
int access;          int expiresIn;      // 默认 3600s
```

#### `GetUploadURLResp`
```java
long fileId;         // Snowflake 预分配
String uploadUrl;    // MinIO Presigned PUT URL
String key;          // MinIO object key
long expiresAt;      // epoch millis
```

#### `ConfirmUploadReq`
```java
long fileId;         long uploaderId;    String md5;   // 可选
```

#### `ConfirmUploadResp`
```java
FileInfo file;
```

#### `GetDownloadURLReq`
```java
long fileId;         long userId;        int expiresIn;
```

#### `GetDownloadURLResp`
```java
String downloadUrl;  long expiresAt;     FileInfo file;
```

#### `GetFileInfoReq`
```java
long fileId;         long userId;
```

#### `GetFileInfoResp`
```java
FileInfo file;
```

#### `BatchGetFileInfoReq`
```java
List<Long> fileIds;  long userId;
```

#### `BatchGetFileInfoResp`
```java
List<FileInfo> files;
```

#### `DeleteFileReq`
```java
long fileId;         long userId;
```

#### `FileInfo`
```java
long   fileId;       String name;        String key;
long   size;         String mimeType;   String ext;
int    width;        int    height;      int    duration;
String md5;          int    purpose;     int    access;
long   uploaderId;   String bucket;     long   createdAt;
```

#### `UploadAvatarReq`
```java
long userId;         String mimeType;   // jpeg/png/webp
// 注：MAIM 用 bytes 传输头像数据；Java 侧改为 Presigned URL 模式更合理，
// Phase 1 简化为走 GetUploadURL(purpose=AVATAR) + ConfirmUpload 两步走，
// 不单独建 UploadAvatarReq/Resp
```

**简化说明**：Phase 1 不建 `UploadAvatarReq/Resp`，头像上传走 `GetUploadURL(purpose=2)` + `ConfirmUpload` 标准流程。

---

### 6.3 friend-service DTO

```
rpc/dto/friend/
├── SendFriendRequestReq.java
├── SendFriendRequestResp.java
├── AcceptFriendRequestReq.java
├── RejectFriendRequestReq.java
├── CancelFriendRequestReq.java
├── ListFriendRequestsReq.java
├── ListFriendRequestsResp.java
├── FriendRequestDTO.java
├── ListFriendsReq.java
├── ListFriendsResp.java
├── FriendInfoDTO.java
├── DeleteFriendReq.java
├── SetRemarkReq.java
├── FriendGroupDTO.java
├── ListGroupsReq.java
├── ListGroupsResp.java
├── CreateGroupReq.java
├── CreateGroupResp.java
├── BlockUserReq.java
├── UnblockUserReq.java
├── ListBlacklistReq.java
├── ListBlacklistResp.java
├── IsBlockedReq.java
└── IsBlockedResp.java
```

**字段明细**（关键类，其余从命名可推断）：

#### `SendFriendRequestReq`
```java
long fromUserId;     long toUserId;      String message;
```
#### `SendFriendRequestResp`
```java
long requestId;
```
#### `AcceptFriendRequestReq`
```java
long requestRequestId; long userId;
```
#### `ListFriendRequestsReq`
```java
long userId;         int pageNum;        int pageSize;      // status: pending
```
#### `FriendRequestDTO`
```java
long requestId;      long fromUserId;    String fromUsername;
String fromAvatar;   long toUserId;      String message;
int status;          long createdAt;     long updatedAt;
```
#### `ListFriendsReq`
```java
long userId;         Long groupId;       // 可选，null=全部
int pageNum;         int pageSize;
```
#### `FriendInfoDTO`
```java
long userId;         String username;    String avatar;
String remark;       long groupId;       String groupName;
String status;       long createdAt;
```

#### `FriendGroupDTO`
```java
long id;             String name;        int sortOrder;     int friendCount;
```

---

### 6.4 conv-service DTO

```
rpc/dto/conv/
├── CreateConversationReq.java
├── CreateConversationResp.java
├── ConversationDTO.java
├── GetConversationReq.java
├── ListConversationsReq.java
├── ListConversationsResp.java
├── AddMembersReq.java
├── AddMembersResp.java
├── RemoveMembersReq.java
├── GetMembersReq.java
├── GetMembersResp.java
├── ConversationMemberDTO.java
├── IsMemberReq.java
├── IsMemberResp.java
├── PreCheckSendReq.java
├── PreCheckSendResp.java
├── MarkReadReq.java
├── UpdateLastMessageReq.java
├── MuteMemberReq.java
├── TransferOwnerReq.java
├── UpdateSettingsReq.java
├── GetSettingsReq.java
└── GetSettingsResp.java
```

**字段明细**（关键类）：

#### `CreateConversationReq`
```java
int type;            // ConvType.SINGLE(1) / GROUP(2)
long creatorId;
Long peerUserId;     // 单聊时指定对方
String name;         // 群聊名称
String avatar;
List<Long> memberIds; // 群聊时初始成员
```
#### `CreateConversationResp`
```java
long conversationId;  ConversationDTO conversation;
```
#### `ConversationDTO`
```java
long id;             int type;           String name;
String avatar;       long ownerId;       int memberCount;
long maxSeq;         long lastMessageId; String lastMessagePreview;
String announcement; boolean isMutedAll; long createdAt;
long updatedAt;
```
#### `ConversationMemberDTO`
```java
long userId;         String username;    String avatar;
int role;            // MemberRole
String alias;        long joinedAt;      long lastReadSeq;
boolean isMuted;     long muteUntil;     int memberType; // 1=user 2=bot
long botId;
```

#### `PreCheckSendReq`
```java
long conversationId;  long userId;
```
#### `PreCheckSendResp`
```java
boolean isMember;    boolean isMuted;    boolean isMutedAll;
long muteUntil;      int convType;       List<Long> memberIds;
```
**学习点**：`PreCheckSend` 是 message-service 发消息前调 conv-service 做的"前置校验"——一次 RPC 拿到所有检查结果（是否成员/是否禁言/成员列表），避免多次 RPC 往返。这是微服务里"聚合校验接口"的经典模式。

#### `UpdateLastMessageReq`
```java
long conversationId;  long lastMessageId;
long maxSeq;          String lastMessagePreview;
```

---

### 6.5 message-service DTO

```
rpc/dto/message/
├── SendMessageReq.java
├── SendMessageResp.java
├── MessageDTO.java
├── RecallMessageReq.java
├── EditMessageReq.java
├── DeleteMessageReq.java
├── GetMessagesReq.java
├── GetMessagesResp.java
├── SyncMessagesReq.java
├── SyncMessagesResp.java
├── GetMessageByIdReq.java
├── BatchGetMessagesReq.java
├── BatchGetMessagesResp.java
├── SearchMessagesReq.java
└── SearchMessagesResp.java
```

**字段明细**：

#### `SendMessageReq`
```java
long conversationId;  long fromUserId;    int msgType;       // MsgType
Map<String, Object> content;              // JSONB 内容，结构因 msgType 而异
Long replyToId;       // 可选，引用回复目标
String clientMsgId;   // 幂等 key
```
#### `SendMessageResp`
```java
long messageId;      long seq;           long createdAt;
```

#### `MessageDTO`
```java
long messageId;      long conversationId; long seq;
long fromUserId;     int msgType;         int status;       // MsgStatus
Map<String, Object> content;
long replyToId;      long replyToMessageId;  // 引用消息 ID
String replyToPreview;                    // 引用消息摘要（冗余，避免再查一次）
int editCount;       long editedAt;       long createdAt;
```
**关于 oneof content 的 Java 方案**：MAIM proto 用 `oneof content` 表达消息内容。Java 侧不建 sealed interface 或多态（Dubbo Hessian2 对多态支持不友好），而是统一用 `Map<String, Object> content`——和 MAIM Go 侧用 `map[string]any` 完全对齐。消费端按 `msgType` 解释 `content` 的字段。

#### `RecallMessageReq`
```java
long messageId;      long conversationId;  long userId;
```
#### `EditMessageReq`
```java
long messageId;      long conversationId;  long userId;
Map<String, Object> newContent;           // 目前只允许文本编辑
```
#### `DeleteMessageReq`
```java
long messageId;      long conversationId;  long userId;       boolean deleteForAll;
```

#### `GetMessagesReq`
```java
long conversationId;  long userId;        String cursor;    // 游标（上一页最后一条 messageId 的 seq）
int limit;            Long beforeTime;    Long afterTime;
```
#### `GetMessagesResp`
```java
List<MessageDTO> messages;  String nextCursor;  boolean hasMore;  long total;
```

#### `SyncMessagesReq`
```java
long conversationId;  long userId;        long fromSeq;     int limit;
```
#### `SyncMessagesResp`
```java
List<MessageDTO> messages;  boolean hasMore;  long maxSeq;
```
**学习点**：`SyncMessages` 是客户端断线重连后的"兜底拉取"——客户端记住上次同步到的 `fromSeq`，请求 `seq > fromSeq` 的全部消息。这是消息不丢失的最终防线。

#### `SearchMessagesReq`
```java
long userId;         String keyword;      Long conversationId;
Long startTime;      Long endTime;        Long senderId;
int pageNum;         int pageSize;
```
#### `SearchMessagesResp`
```java
List<MessageDTO> messages;  long total;
```
**简化说明**：Phase 1 搜索用 PostgreSQL LIKE 模糊查询（`content->>'text' ILIKE '%keyword%'`），Phase 2 再接 ES。DTO 定义提前预留。

---

### 6.6 signaling-service DTO

```
rpc/dto/signaling/
├── NotificationDTO.java
├── ListNotificationsReq.java
├── ListNotificationsResp.java
├── GetUnreadCountResp.java
├── MarkReadReq.java
├── IsOnlineReq.java
├── IsOnlineResp.java
├── BatchIsOnlineReq.java
└── BatchIsOnlineResp.java
```

#### `NotificationDTO`
```java
long id;            long userId;         int type;
String title;       String content;      boolean isRead;
String referenceId; long createdAt;
```
#### `GetUnreadCountResp`
```java
long count;
```
#### `BatchIsOnlineResp`
```java
Map<Long, Boolean> status;
```

---

### 6.7 push (ws-gateway) DTO

```
rpc/dto/push/
└── PushMessage.java   // 推送给 WS 客户端的事件体
```

#### `PushMessage`
```java
String event;                   // WsEvent 常量，如 "message.new"
Map<String, Object> data;       // 事件携带的数据
long timestamp;
```

**学习点**：PushMessage 是 signaling → ws-gateway → 客户端的通用推送格式。`event` 字段告诉客户端这是什么事件，`data` 是事件载荷。统一格式避免每种事件一套接口。

---

## Step 7 — rpc/（Dubbo 服务接口）

> 包路径：`lanshan.manmu.common.rpc`
> 纯 Java interface，无 `@DubboService` 注解（注解由实现端加）
> 每个方法可能抛出 `BizException`

### `rpc/UserRpcService.java`

```java
package lanshan.manmu.common.rpc;

public interface UserRpcService {
    // —— 认证 ——
    RegisterResp register(RegisterReq req);
    LoginResp login(LoginReq req);
    void logout(LogoutReq req);
    ValidateTokenResp validateToken(ValidateTokenReq req);

    // —— 资料 ——
    UserInfo getUserInfo(GetUserInfoReq req);
    BatchGetUserInfoResp batchGetUserInfo(BatchGetUserInfoReq req);
    UserInfo updateProfile(UpdateProfileReq req);
    SearchUsersResp searchUsers(SearchUsersReq req);
    ListAllUserIdsResp listAllUserIds();
}
```

> 共 9 个方法。MAIM 原版 23 个，砍掉了 OAuthLogin/RefreshToken/GetSessions/RevokeSession/Settings/Balance 等 Phase 1 非核心。

### `rpc/FileRpcService.java`

```java
public interface FileRpcService {
    GetUploadURLResp   getUploadURL(GetUploadURLReq req);
    ConfirmUploadResp  confirmUpload(ConfirmUploadReq req);
    GetDownloadURLResp getDownloadURL(GetDownloadURLReq req);
    GetFileInfoResp    getFileInfo(GetFileInfoReq req);
    BatchGetFileInfoResp batchGetFileInfo(BatchGetFileInfoReq req);
    void               deleteFile(DeleteFileReq req);
}
```

### `rpc/FriendRpcService.java`

```java
public interface FriendRpcService {
    // —— 好友申请 ——
    SendFriendRequestResp  sendFriendRequest(SendFriendRequestReq req);
    void acceptFriendRequest(AcceptFriendRequestReq req);
    void rejectFriendRequest(RejectFriendRequestReq req);
    void cancelFriendRequest(CancelFriendRequestReq req);
    ListFriendRequestsResp listFriendRequests(ListFriendRequestsReq req);

    // —— 好友管理 ——
    ListFriendsResp  listFriends(ListFriendsReq req);
    void deleteFriend(DeleteFriendReq req);
    void setRemark(SetRemarkReq req);

    // —— 好友分组 ——
    ListGroupsResp   listGroups(ListGroupsReq req);
    CreateGroupResp  createGroup(CreateGroupReq req);
    void renameGroup(RenameGroupReq req);
    void deleteGroup(DeleteGroupReq req);

    // —— 黑名单 ——
    void blockUser(BlockUserReq req);
    void unblockUser(UnblockUserReq req);
    ListBlacklistResp listBlacklist(ListBlacklistReq req);
    boolean isBlocked(IsBlockedReq req);
}
```

### `rpc/ConvRpcService.java`

```java
public interface ConvRpcService {
    // —— 会话 CRUD ——
    CreateConversationResp createConversation(CreateConversationReq req);
    ConversationDTO getConversation(long conversationId, long userId);
    ListConversationsResp listConversations(ListConversationsReq req);

    // —— 成员管理 ——
    AddMembersResp  addMembers(AddMembersReq req);
    void            removeMembers(RemoveMembersReq req);
    GetMembersResp  getMembers(GetMembersReq req);

    // —— 权限校验（message-service 调用）——
    boolean isMember(long conversationId, long userId);
    PreCheckSendResp preCheckSend(PreCheckSendReq req);

    // —— 已读状态 ——
    void markRead(MarkReadReq req);

    // —— last message 更新（message-service 消费事件后回调）——
    void updateLastMessage(UpdateLastMessageReq req);

    // —— 群管理 ——
    void muteMember(MuteMemberReq req);
    void transferOwner(TransferOwnerReq req);
    void updateAnnouncement(long convId, long operatorId, String content);

    // —— 设置 ——
    GetSettingsResp getSettings(GetSettingsReq req);
    void updateSettings(UpdateSettingsReq req);
}
```

### `rpc/MessageRpcService.java`

```java
public interface MessageRpcService {
    // —— 消息核心 ——
    SendMessageResp sendMessage(SendMessageReq req);
    void            recallMessage(RecallMessageReq req);
    void            editMessage(EditMessageReq req);
    void            deleteMessage(DeleteMessageReq req);

    // —— 消息查询 ——
    GetMessagesResp   getMessages(GetMessagesReq req);
    SyncMessagesResp  syncMessages(SyncMessagesReq req);
    MessageDTO        getMessageById(long messageId);
    BatchGetMessagesResp batchGetMessages(List<Long> messageIds);
    SearchMessagesResp searchMessages(SearchMessagesReq req);
}
```

### `rpc/SignalingRpcService.java`

```java
public interface SignalingRpcService {
    ListNotificationsResp listNotifications(ListNotificationsReq req);
    long getUnreadCount(long userId);
    void markRead(long notificationId, long userId);
    void markAllRead(long userId);
    void deleteNotification(long notificationId);
    boolean isOnline(long userId);
    BatchIsOnlineResp batchIsOnline(List<Long> userIds);
}
```

### `rpc/PushRpcService.java`

```java
/**
 * ws-gateway 内部推送接口。
 * 被 signaling-service 通过 @DubboReference 调用。
 */
public interface PushRpcService {
    /** 推送给指定用户列表（在线则发 WS frame） */
    void pushToUsers(List<Long> userIds, PushMessage message);
    /** 推送给会话所有成员 */
    void pushToConv(long convId, PushMessage message);
    /** 推送给单个用户 */
    void pushToUser(long userId, PushMessage message);
}
```

---

## Step 8 — 文件清单汇总

L0 完成后的 common 模块文件树：

```
common/src/main/java/lanshan/manmu/common/
├── result/
│   ├── Result.java
│   ├── PageResult.java
│   └── CursorResult.java
├── exception/
│   ├── ErrorCode.java           (枚举, ~60 行)
│   └── BizException.java
├── constant/
│   ├── KafkaTopic.java
│   ├── KafkaGroup.java
│   ├── MsgType.java
│   ├── MsgStatus.java
│   ├── ConvType.java
│   ├── MemberRole.java
│   ├── WsEvent.java
│   └── CommonConst.java
├── event/
│   ├── MessageCreatedEvent.java
│   ├── MessageRecalledEvent.java
│   ├── MessageEditedEvent.java
│   ├── MessageDeletedEvent.java
│   ├── ConversationReadUpdatedEvent.java
│   ├── MemberJoinedEvent.java
│   └── MemberLeftEvent.java
├── util/
│   └── SnowflakeIdWorker.java
├── rpc/
│   ├── UserRpcService.java
│   ├── FileRpcService.java
│   ├── FriendRpcService.java
│   ├── ConvRpcService.java
│   ├── MessageRpcService.java
│   ├── SignalingRpcService.java
│   ├── PushRpcService.java
│   └── dto/
│       ├── user/           (16 个 DTO)
│       ├── file/           (9 个 DTO)
│       ├── friend/         (20 个 DTO)
│       ├── conv/           (17 个 DTO)
│       ├── message/        (14 个 DTO)
│       ├── signaling/      (7 个 DTO)
│       └── push/
│           └── PushMessage.java
└── common/pom.xml          (已有, 需确认 lombok/fastjson2/hutool 依赖齐全)
```

**文件总数**：约 85 个 Java 类 + 1 个 pom 修改。

---

## Step 9 — pom.xml 依赖检查

`common` 当前 pom.xml 已有 lombok/fastjson2/hutool。需确认：

1. **MapStruct** 不加在 common（无 Entity→DTO 转换）
2. **Jackson 注解** 需要加：用于 `@JsonProperty` 映射 snake_case
3. **Lombok** 需高版本支持 `@NoArgsConstructor` / `@AllArgsConstructor`

**pom 补充**：
```xml
<!-- Jackson 注解 (用于 event DTO 的 @JsonProperty) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
</dependency>
```

> 注：Jackson 版本由 Spring Boot BOM 管理，无需在 common 声明版本。

**Dubbo 不加入 common**：接口只是 Java interface，实现端用 `@DubboService` 暴露，消费端用 `@DubboReference` 引入。common 只提供接口定义，不依赖 Dubbo 框架——保持其为"纯接口/DTO/工具"模块，规约与 AGENTS.md 一致。

---

## Step 10 — 验证清单

```
步骤                                                验证方法
─────────────────────────────────────────────────
1. pom.xml 补充 jackson-annotations                     mvn compile -pl common 成功
2. 建全部 result 实体类                                 mvn compile 成功
3. 建异常体系                                           mvn compile 成功
4. 建全部 constant                                      mvn compile 成功
5. 建全部 event DTO                                     mvn compile 成功
6. 建 SnowflakeIdWorker                                 mvn compile 成功
7. 建 rpc/dto + rpc 接口                                mvn compile -pl common 成功
8. SnowflakeIdWorker 单测                          mvn test -pl common
9. Event DTO JSON 序列化/反序列化一致性单测          mvn test -pl common
10. 整体 mvn compile 验证依赖完整                     mvn compile 全量成功
```

**验证要点**：
- **SnowflakeIdWorker 单测**：验证 10000 次 `nextId()` 严格递增、`extractTimestamp` 正确、不同 workerId 生成的 ID 不冲突
- **Event 序列化单测**：建 `MessageCreatedEvent` → `JSON.toJSONString` → `JSON.parseObject` 回来，字段值一致，特别验证 snake_case 字段名
- **不写 Dubbo 接口单测**：接口无实现无法直接测，验证编译通过即可

---

## L0 学习源码要点

L0 实现完成后，主人逐文件对照学习时重点关注：

1. **`SnowflakeIdWorker`** — 位运算、时钟回拨、并发安全（synchronized）
2. **`ErrorCode` 枚举** — 错误码分段设计，思考为什么用户类放 10xxx 而不是 1xxx
3. **`MessageCreatedEvent`** — Kafka 事件契约，思考 producer 和 consumer 如何共用一个 DTO 保证两端一致
4. **Dubbo 接口** — 思考"为什么接口放 common 而不是各服务自己定义"（答案：消费方需要依赖接口编译）
5. **`PreCheckSendResp`** — 聚合校验接口模式，一次 RPC 拿回多个校验结果
6. **`PushMessage`** — 统一推送格式设计，`event + data` 是事件驱动客户端的标准模式

---

## Phase 1 接口实现范围对齐

以下 RPC 方法在 L0 定义接口但 **L1~L4 不实现**，留为 Phase 2 从使用（标注 `// Phase 2`）：

- `UserRpcService`: 无预留（Phase 1 全部实现 9 个方法）
- `ConvRpcService`: `addBot/removeBot/listBots` — Bot 绑定相关，Phase 2 扩展
- `MessageRpcService`: `sendBotReply/sendSystemMessage` — Bot 回复相关，Phase 2 扩展
- `SignalingRpcService`: 全部 Phase 1 实现

接口定义时直接不写 Phase 2 方法，避免编译器报"未实现接口"。后续 Phase 2 时反向在接口加方法。
```