# conv-service 详细实现 spec

> **目标**：实现会话域核心服务，覆盖单聊/群聊的创建、成员管理、权限校验、已读机制、Last Message 维护。
> **参考**：MAIM（Go 版）`app/conversation-service` 的业务逻辑 + AIM `file-service` / `user-service` 的代码风格模板。
> **前置条件**：common 模块的 `ConvRpcService` 接口（15 方法）+ 19 个 DTO 已定义；`conv` schema 5 张表已建好；user-service 已上线。
> **配置规范**：严格遵循 [nacos-config-spec.md](./nacos-config-spec.md)，业务配置全部放 Nacos DataId，本地 application.yml 只管「连 Nacos + 触发拉取 + 兜底」。

---

## 0. 设计决策清单（已确认）

| # | 决策项 | 值 | 理由 |
|---|--------|-----|------|
| 1 | 角色编码 | `OWNER=1, ADMIN=2, MEMBER=3`（数字越小权限越大） | 直接复用 common `MemberRole.*` 常量，不重复定义 |
| 2 | 单聊 `ownerId` | `0`（无群主概念） | 单聊无群主，仅群聊 `ownerId=创建者` |
| 3 | 单聊去重 | 双 JOIN 查询 `conv_members` | 复用 MAIM 成熟方案 |
| 4 | `memberType` 映射 | DB `VARCHAR('user'/'bot')` ↔ DTO `int(1/2)` | DB 可读性 + DTO 紧凑性 |
| 5 | `conv_bots` 表 | Phase 1 不使用，表结构预留 | AGENTS.md 要求 Phase 1 不引入 AI 依赖 |
| 6 | 未读计数维护 | conv-service 只负责 `markRead` 时 **Clear**；Incr 由 message-service 负责 | 职责单一 |
| 7 | 系统消息发送 | 通过 `@DubboReference MessageRpcService` 同步调用 | AIM 无 goroutine |
| 8 | 事件发布 | Kafka 直接 send（Phase 1 简化）；事件体用 common 已有 DTO 类；topic/group 用 `KafkaTopic.*` / `KafkaGroup.*` 常量 | 先跑通链路 + DRY |
| 9 | 事务边界 | `createConversation`、`transferOwner`、`addMembers`、`removeMembers` 包 `@Transactional` | 改进 MAIM 缺陷 |
| 10 | 事务后置操作 | Redis DEL / Kafka send 必须在 DB 事务**提交后**执行，用 `@TransactionalEventListener(phase=AFTER_COMMIT)` | 避免 Redis/Kafka 失败导致 DB 回滚；外部系统不污染事务 |
| 11 | 配置校验 | 名称/公告/备注长度在 Service 层用 `BAD_REQUEST + detail` | 改进 MAIM 缺陷 |
| 12 | 权限校验 | 统一 `PermissionChecker` 工具类，复用 `MemberRole.*` 常量 | DRY 原则 |
| 13 | 孤儿 DTO 清理 | 删除 common 中的 `GetConversationReq`、`IsMemberReq`、`IsMemberResp`（接口已改用 `(long, long)` + `boolean`，这 3 个 DTO 是孤儿代码） | YAGNI |
| 14 | `PreCheckSend` 设计 | 信息收集型，不做拦截决策 | 关注点分离 |
| 15 | Snowflake workerId | `3`（user=0, file=4, conv=3） | 与端口 20883 对应 |
| 16 | SnowflakeConfig 不加 @RefreshScope | `workerId` 是启动期固定值，热刷新会导致 ID 重复 | 与 user/file-service 对齐 |
| 17 | Kafka 消费组 | 配置在 Nacos `spring.kafka.consumer.group-id: conv-service`，**不在 `@KafkaListener` 注解里指定 groupId**（注解会覆盖配置，使 Nacos 配置失效） | Spring Boot 惯例 |
| 18 | Kafka topic | 复用 `KafkaTopic.*` 常量，不写魔法字符串 | DRY |
| 19 | 配置管理 | 遵循 nacos-config-spec：业务配置放 Nacos DataId `conv-service.yml`，本地 application.yml 只放连接属性 + 触发拉取 | 单一配置源 |
| 20 | Phase 1 事件范围 | 只发 common 已有的 3 个事件：`MemberJoinedEvent`/`MemberLeftEvent`/`ConversationReadUpdatedEvent`；`conversation.created`/`owner.transferred` 留待 Phase 2（需先在 common 补事件类） | YAGNI |
| 21 | UPSERT 实现 | 用 PG 原生 `INSERT ... ON CONFLICT (...) DO UPDATE ...` 自定义 SQL，不用 select-then-insert（避免竞态） | 并发安全 |
| 22 | preview 生成 | 由 message-service 在 `MessageCreatedEvent` 里带 `preview` 字段，conv-service 只透传，不跨服务猜测 content 结构 | 职责边界 |
| 23 | Boolean 字段映射 | Entity Boolean 字段（如 `isMuted`）加 `@TableField("is_muted")` 显式标注列名，避免 MP 版本差异 | 防御性 |

---

## 1. DB Schema（无变更）

`conv` schema 5 张表已在 `docs/sql/auto/schemas/aim-schema.sql` 中定义。

| 表 | 用途 | Phase 1 |
|----|------|:---:|
| `conv.conversations` | 会话主表 | ✅ |
| `conv.conv_members` | 成员关系表 | ✅ |
| `conv.conv_read_seqs` | 已读位置表 | ✅ |
| `conv.conv_settings` | 用户会话设置表 | ✅ |
| `conv.conv_bots` | Bot 绑定表（Phase 2） | ❌ |

**关键字段约定**：
- `conversations.owner_id`：单聊=0，群聊=创建者 userId
- `conv_members.member_type`：DB 存 `'user'`/`'bot'`，DTO 传 `1`/`2`
- `conv_members.role`：1=OWNER, 2=ADMIN, 3=MEMBER（与 `MemberRole.*` 一致）
- `conv_members.mute_until`：epoch 秒（非毫秒），0=永久或未禁言
- 已有索引：`idx_conv_members_pair(conv_id, user_id)`（单聊去重 SQL 会用到）

---

## 2. common 模块改动

### 2.1 ErrorCode（无需新增）

现有 9 个 conv 错误码（30001~30009）已覆盖所有场景：`CONV_NOT_FOUND`、`CONV_MEMBER_EXISTS`、`CONV_MEMBER_NOT_FOUND`、`CONV_NOT_MEMBER`、`CONV_PERMISSION_DENIED`、`CONV_MUTED`、`CONV_MUTED_ALL`、`CONV_MEMBER_LIMIT`、`CONV_OWNER_TRANSFER_SELF`。

长度校验用 `BAD_REQUEST(400) + detail`，角色同级保护用 `CONV_PERMISSION_DENIED + detail`。

### 2.2 ConvRpcService 接口（已定义，无需改动）

接口共 15 方法，签名如下（**直接传基本类型，不包装 Req DTO**）：

```java
// 已存在的接口签名（不要改动）
CreateConversationResp createConversation(CreateConversationReq req);
ConversationDTO getConversation(long conversationId, long userId);   // 直接传 long
ListConversationsResp listConversations(ListConversationsReq req);
AddMembersResp addMembers(AddMembersReq req);
void removeMembers(RemoveMembersReq req);
GetMembersResp getMembers(GetMembersReq req);
boolean isMember(long conversationId, long userId);                  // 返回 boolean
PreCheckSendResp preCheckSend(PreCheckSendReq req);
void markRead(MarkReadReq req);
void updateLastMessage(UpdateLastMessageReq req);
void muteMember(MuteMemberReq req);
void transferOwner(TransferOwnerReq req);
void updateAnnouncement(long convId, long operatorId, String content); // 直接传 3 个参数
GetSettingsResp getSettings(GetSettingsReq req);
void updateSettings(UpdateSettingsReq req);
```

> **注**：`getConversation`/`isMember`/`updateAnnouncement` 不走 Req DTO，直接传基本类型。**实际使用 DTO 数 16 个**（19 - 3 个孤儿）。

### 2.3 删除孤儿 DTO（决策 13）

common 中以下 3 个 DTO 是孤儿代码（接口已改用基本类型，这些 DTO 未被任何地方引用）：

```bash
rm backend/common/src/main/java/lanshan/manmu/common/rpc/dto/conv/GetConversationReq.java
rm backend/common/src/main/java/lanshan/manmu/common/rpc/dto/conv/IsMemberReq.java
rm backend/common/src/main/java/lanshan/manmu/common/rpc/dto/conv/IsMemberResp.java
```

### 2.4 事件 DTO（已存在，直接复用）

common 模块已有 3 个 conv 相关事件类，**conv-service 必须直接复用，不要自己构造 Map**：

| 事件类 | 字段（JSON snake_case） | 触发场景 |
|--------|------------------------|----------|
| `MemberJoinedEvent` | `conv_id`, `user_ids`(List), `joined_by` | addMembers 成功后 |
| `MemberLeftEvent` | `conv_id`, `user_ids`(List), `removed_by` | removeMembers 成功后 |
| `ConversationReadUpdatedEvent` | `conv_id`, `user_id`, `last_read_seq` | markRead 成功后 |

> **Phase 1 不发** `conversation.created` 和 `conversation.owner.transferred`，因为 common 没有对应事件类（决策 20）。如需补，先在 common 加 `ConversationCreatedEvent` / `OwnerTransferredEvent` 再发。

### 2.5 MessageCreatedEvent 需要补 preview 字段（决策 22）

当前 `MessageCreatedEvent` 没有 `preview` 字段，conv-service 消费时无法拿到预览文本。**需要先在 common 给 `MessageCreatedEvent` 加 `preview` 字段**：

```java
// MessageCreatedEvent.java（需修改）
@JsonProperty("preview")
private String preview;   // 由 message-service 生成，conv-service 只透传
```

> **职责边界**：preview 文本由 message-service 根据 `msg_type` + `content` 生成（如文本取前 50 字符、图片显示「[图片]」），conv-service 不跨服务猜测 content 结构。

### 2.6 UpdateLastMessageReq 字段说明（供消费端参考）

`UpdateLastMessageReq` 只有 `@Data @NoArgsConstructor @AllArgsConstructor`，**无 `@Builder`**。字段如下（构造顺序）：

```java
private long conversationId;
private long lastMessageId;
private long maxSeq;
private String lastMessagePreview;
```

**正确构造方式**：`new UpdateLastMessageReq(convId, msgId, seq, preview)`，**不要用 builder**。

---

## 3. conv-service pom.xml

**现状**：`conv-service/pom.xml` 已存在但缺少关键依赖。**需新增** 5 个依赖：

```xml
<!-- 新增 1: Nacos 配置中心（配合 application.yml 的 spring.config.import 拉取，无需 bootstrap 依赖） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- 新增 2: MyBatis-Plus 分页插件 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>

<!-- 新增 3: Kafka（消费 message.created + 发送 conv 事件） -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- 新增 4: Spring Boot 基础 starter（与 file-service 对齐） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>

<!-- 新增 5: 测试支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**完整 pom.xml 依赖段**（参考 file-service 风格，版本由父 POM 管理）：

```xml
<dependencies>
    <dependency>
        <groupId>lanshan.manmu</groupId>
        <artifactId>aim-common</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-jsqlparser</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <!-- Nacos 配置中心（配合 application.yml 的 spring.config.import 拉取，无需 bootstrap 依赖） -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> **不需要** `spring-boot-starter-web`（纯 Dubbo Provider，不暴露 HTTP 端口）。
> **不需要** `spring-cloud-starter-bootstrap`（本项目用 `spring.config.import` 触发拉取，见 nacos-config-spec 第 2.3 节）。

---

## 4. application.yml（本地兜底 + Nacos 触发拉取）

**严格遵循 nacos-config-spec 第 2.2 节模板**。本地 application.yml **只放**：① Nacos 连接属性 ② spring.config.import 触发拉取 ③ 最小兜底。**不放任何业务配置**（datasource/redis/kafka/dubbo/mybatis-plus/aim.snowflake 全部放 Nacos DataId）。

```yaml
# application.yml
# 1) Nacos 连接属性 + 触发拉取  2) 最小兜底
# 业务配置全部在 Nacos 配置中心对应 namespace 的 conv-service.yml DataId 下
# conv-service 是纯 Dubbo Provider，不对外提供 HTTP 端口
spring:
  application:
    name: conv-service
  cloud:
    nacos:
      # 公共连接属性，config / discovery 模块自动继承
      server-addr: ${NACOS_ADDR:localhost:8848}
      namespace: ${NACOS_NAMESPACE:dev}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      # group 不能放公共层（discovery 默认 DEFAULT_GROUP，config 才用 AIM_GROUP）
      config:
        group: ${NACOS_GROUP:AIM_GROUP}
        file-extension: yml
      discovery:
        group: ${NACOS_GROUP:AIM_GROUP}
  config:
    import:
      # optional: 表示 Nacos 不可达时不阻塞启动（走兜底）
      # 第二条拉共享公共配置
      - optional:nacos:conv-service.yml
      - optional:nacos:application.yml?group=COMMON_GROUP
```

**关键点**（与 user-service / file-service 完全对齐）：
- `spring.application.name` 必须与 Nacos 里的 DataId 文件名一致（`conv-service.yml`）
- `server-addr / namespace / username / password` 放 `spring.cloud.nacos` **公共层**，config 和 discovery 自动继承
- `namespace` 默认值 `dev`（不是空字符串）
- `group` **不放公共层**——discovery 默认 `DEFAULT_GROUP`，config 用 `AIM_GROUP`
- `spring.config.import` 是触发 Nacos 拉取的**唯一开关**：第一条拉本服务 DataId，第二条拉共享公共配置
- **禁止**新建 `bootstrap.yml`（见 nacos-config-spec 第 2.3 节）

---

## 5. Nacos DataId 配置（conv-service.yml）

业务配置全部放在 Nacos 配置中心 `conv-service.yml`（group=`AIM_GROUP`，namespace=`dev|test|prod`）。**敏感值用占位符，由环境变量注入**。

### 5.1 DataId 完整内容

```yaml
# conv-service 主配置（敏感值由环境变量注入）
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/aim?currentSchema=%22conv%22&stringtype=unspecified
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: conv-service        # 与 KafkaGroup.CONV_SERVICE 常量值一致
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

dubbo:
  application:
    name: conv-service
  protocol:
    name: dubbo
    port: 20883
  registry:
    # Dubbo 3.3.x 独立调 Nacos OpenAPI，必须带鉴权（见 nacos-config-spec 第 3.4 节）
    address: nacos://${NACOS_USERNAME:nacos}:${NACOS_PASSWORD:nacos}@${NACOS_ADDR:localhost:8848}?namespace=${NACOS_NAMESPACE:dev}&group=${NACOS_GROUP:AIM_GROUP}

aim:
  snowflake:
    worker-id: 3
```

**关键约定**（对照 nacos-config-spec）：
- `currentSchema=%22conv%22`：`conv` 是 PG 保留字（conversion），**必须用双引号包裹**，URL 编码为 `%22`（与 user-service 的 `%22user%22` 风格一致）
- `dubbo.registry.address` **必须带鉴权**：`nacos://user:pass@host:port?namespace=xxx&group=yyy`，否则 Dubbo 匿名访问 Nacos OpenAPI 会 401 卡死
- `namespace=${NACOS_NAMESPACE:dev}` 默认值 `dev`（不是空字符串）
- `mybatis-plus.configuration.map-underscore-to-camel-case` **不写在这里**，已在共享配置 `COMMON_GROUP/application.yml` 里定义
- `spring.kafka.consumer.group-id` 写死 `conv-service`，**`@KafkaListener` 注解不再指定 groupId**（决策 17，避免覆盖配置）

### 5.2 nacos-init-data.sql 更新

需在 `docs/sql/init/nacos-init-data.sql` 的 `DO $$ ... $$` 块里追加 conv-service 的 DataId。

**步骤 1**：在 `DECLARE` 区追加 `conv_svc_content` 变量（与 user_svc_content / file_svc_content 并列）：

```sql
conv_svc_content TEXT := E'# conv-service 主配置（敏感值由环境变量注入）\nspring:\n  datasource:\n    url: jdbc:postgresql://${DB_HOST:localhost}:5432/aim?currentSchema=%22conv%22&stringtype=unspecified\n    username: ${DB_USER:postgres}\n    password: ${DB_PASSWORD:postgres}\n    driver-class-name: org.postgresql.Driver\n  data:\n    redis:\n      host: ${REDIS_HOST:localhost}\n      port: ${REDIS_PORT:6379}\n  kafka:\n    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}\n    consumer:\n      group-id: conv-service\n      auto-offset-reset: earliest\n      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer\n      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer\n    producer:\n      key-serializer: org.apache.kafka.common.serialization.StringSerializer\n      value-serializer: org.apache.kafka.common.serialization.StringSerializer\ndubbo:\n  application:\n    name: conv-service\n  protocol:\n    name: dubbo\n    port: 20883\n  registry:\n    address: nacos://${NACOS_USERNAME:nacos}:${NACOS_PASSWORD:nacos}@${NACOS_ADDR:localhost:8848}?namespace=${NACOS_NAMESPACE:dev}&group=${NACOS_GROUP:AIM_GROUP}\naim:\n  snowflake:\n    worker-id: 3\n';
```

**步骤 2**：在 `FOREACH ns IN ARRAY ns_list LOOP` 里追加一条 INSERT（与 user/file 的 INSERT 并列）：

```sql
INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
VALUES ('conv-service.yml', 'AIM_GROUP', conv_svc_content, md5(conv_svc_content), '127.0.0.1', ns, 'yaml')
ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;
```

**步骤 3**：执行脚本 + 重启 Nacos（幂等，已有 DataId 不会被覆盖）：

```bash
psql -h localhost -U postgres -d nacos -f docs/sql/init/nacos-init-data.sql
docker compose restart nacos
```

### 5.3 验证 DataId 真实存储内容

```bash
TOKEN=$(curl -s -X POST 'http://localhost:8848/nacos/v1/auth/users/login' \
    -d 'username=nacos&password=nacos' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -s "http://localhost:8848/nacos/v3/admin/cs/config?accessToken=${TOKEN}&namespaceId=dev&groupName=AIM_GROUP&dataId=conv-service.yml"
```

---

## 6. 启动类 + 包结构

### 6.1 启动类

```java
package lanshan.manmu.conv;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
@MapperScan("lanshan.manmu.conv.mapper")
public class ConvServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConvServiceApplication.class, args);
    }
}
```

> **不需要** `@EnableScheduling`（conv-service 无定时任务，与 file-service 的 zombie 清理不同）。

### 6.2 包结构（与 user/file-service 对齐）

```
lanshan.manmu.conv/
├── ConvServiceApplication.java
├── config/                  # 配置类
│   ├── MybatisPlusConfig.java
│   ├── SnowflakeConfig.java
│   └── KafkaProducerConfig.java
├── consumer/                # Kafka 消费者
│   └── ConvMessageConsumer.java
├── mapper/                  # MyBatis-Plus Mapper
│   ├── ConversationMapper.java
│   ├── ConversationMemberMapper.java
│   ├── ConvReadSeqMapper.java
│   └── ConvSettingsMapper.java
├── model/
│   ├── dto/                 # 服务内部 DTO（如有）
│   └── entity/              # 实体类
│       ├── Conversation.java
│       ├── ConversationMember.java
│       ├── ConvReadSeq.java
│       └── ConvSettings.java
├── event/                   # 事件相关（发布器、监听器）
│   ├── ConvEventPublisher.java
│   └── ConvEventListener.java   # @TransactionalEventListener
├── rpc/                     # Dubbo Provider 实现
│   └── ConvRpcServiceImpl.java
├── service/                 # 业务服务
│   ├── ConvService.java         # 接口
│   └── impl/
│       └── ConvServiceImpl.java # 实现
└── util/                    # 工具类
    ├── PermissionChecker.java
    ├── UnreadCacheService.java
    └── ConvConstants.java
```

> **与 user/file-service 对齐**：实体放 `model/entity/`（不是 `entity/`），工具放 `util/`（不是 `tool/`），Service 接口与实现分离（`service/` + `service/impl/`），**无 `controller` 包**（纯 Dubbo Provider 不暴露 HTTP）。

---

## 7. Entity（4 个，包路径 `model/entity/`）

**与 user/file-service 风格对齐**：`@Data + @NoArgsConstructor + @AllArgsConstructor + @TableName`，主键 `@TableId`，Boolean 字段加 `@TableField` 显式标注列名（决策 23，避免 MP 版本差异）。

```java
package lanshan.manmu.conv.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversations")
public class Conversation {
    @TableId
    private Long id;
    private Integer type;
    private String name;
    private String avatar;
    private Long ownerId;
    private String announcement;
    @TableField("is_muted_all")
    private Boolean isMutedAll;
    private String background;
    private Long maxSeq;
    private Long lastMessageId;
    private String lastMessagePreview;
    private Integer memberCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_members")
public class ConversationMember {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    private String memberType;
    private Long botId;
    private Integer role;
    private String alias;
    @TableField("is_muted")
    private Boolean isMuted;
    private Long muteUntil;
    private OffsetDateTime joinedAt;
}
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_read_seqs")
public class ConvReadSeq {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    private Long lastReadSeq;
    private OffsetDateTime readAt;
}
```

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_settings")
public class ConvSettings {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    @TableField("is_muted")
    private Boolean isMuted;
    @TableField("is_pinned")
    private Boolean isPinned;
}
```

**memberType 映射**：DB `'user'`/`'bot'` ↔ DTO `1`/`2`，Service 层 `toDbType(int)`/`toDtoType(String)` 转换（用 `ConvConstants.MEMBER_TYPE_*`）。

---

## 8. Mapper（4 个 + UPSERT 自定义 SQL）

### 8.1 ConversationMapper（含单聊去重 SQL）

```java
package lanshan.manmu.conv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.conv.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT c.* FROM conversations c " +
            "JOIN conv_members m1 ON m1.conv_id = c.id AND m1.user_id = #{userId1} " +
            "JOIN conv_members m2 ON m2.conv_id = c.id AND m2.user_id = #{userId2} " +
            "WHERE c.type = 1 LIMIT 1")
    Conversation findPrivateConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
```

> 单聊去重 SQL 依赖 `idx_conv_members_pair(conv_id, user_id)` 索引，已在 schema 中建好。

### 8.2 ConversationMemberMapper

```java
@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {}
```

### 8.3 ConvReadSeqMapper（含 UPSERT，决策 21）

```java
@Mapper
public interface ConvReadSeqMapper extends BaseMapper<ConvReadSeq> {

    /**
     * UPSERT 已读位置（PG 原生语法，并发安全）。
     * last_read_seq 只增不减：用 GREATEST 保证不会回退。
     */
    @Update("INSERT INTO conv_read_seqs (id, conv_id, user_id, last_read_seq, read_at) " +
            "VALUES (#{id}, #{convId}, #{userId}, #{lastReadSeq}, NOW()) " +
            "ON CONFLICT (conv_id, user_id) DO UPDATE " +
            "SET last_read_seq = GREATEST(conv_read_seqs.last_read_seq, EXCLUDED.last_read_seq), " +
            "    read_at = NOW()")
    int upsertReadSeq(@Param("id") Long id, @Param("convId") Long convId,
                      @Param("userId") Long userId, @Param("lastReadSeq") Long lastReadSeq);
}
```

### 8.4 ConvSettingsMapper（含 UPSERT，决策 21）

```java
@Mapper
public interface ConvSettingsMapper extends BaseMapper<ConvSettings> {

    /**
     * UPSERT 用户会话设置。
     * 用 COALESCE 处理「null=不更新」语义（Boolean 包装类型）。
     */
    @Update("INSERT INTO conv_settings (id, conv_id, user_id, is_muted, is_pinned) " +
            "VALUES (#{id}, #{convId}, #{userId}, " +
            "        COALESCE(#{isMuted}, false), COALESCE(#{isPinned}, false)) " +
            "ON CONFLICT (conv_id, user_id) DO UPDATE " +
            "SET is_muted  = COALESCE(#{isMuted},  conv_settings.is_muted), " +
            "    is_pinned = COALESCE(#{isPinned}, conv_settings.is_pinned)")
    int upsertSettings(@Param("id") Long id, @Param("convId") Long convId,
                       @Param("userId") Long userId,
                       @Param("isMuted") Boolean isMuted, @Param("isPinned") Boolean isPinned);
}
```

> **为什么不用 select-then-insert/update**：并发场景下两个线程都 select 到 null，都执行 insert 会冲突；用 PG 原生 `ON CONFLICT` 是原子操作，无竞态。

---

## 9. Config 类（3 个）

### 9.1 MybatisPlusConfig（复制 file-service）

```java
package lanshan.manmu.conv.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

### 9.2 SnowflakeConfig（workerId=3，**不加 @RefreshScope**，决策 16）

```java
package lanshan.manmu.conv.config;

import lanshan.manmu.common.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake ID 生成器配置（conv-service workerId=3）。
 * <p>不加 @RefreshScope：workerId 是启动期固定值，运行时变更会导致 ID 重复。
 * 与 user-service / file-service 保持一致。
 */
@Configuration
public class SnowflakeConfig {

    private final long workerId;

    public SnowflakeConfig(@Value("${aim.snowflake.worker-id:3}") long workerId) {
        this.workerId = workerId;
    }

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId);
    }
}
```

### 9.3 KafkaProducerConfig

```java
package lanshan.manmu.conv.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

---

## 10. 权限校验工具 PermissionChecker

**复用 common `MemberRole.*` 常量**（决策 12，不重复定义角色码）。

```java
package lanshan.manmu.conv.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.model.entity.ConversationMember;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {

    private final ConversationMemberMapper memberMapper;

    public PermissionChecker(ConversationMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /** 要求操作者角色 <= minRole。返回操作者 member 记录供复用 */
    public ConversationMember requireRole(long convId, long operatorId, int minRole) {
        ConversationMember member = getMember(convId, operatorId);
        if (member == null) {
            throw new BizException(ErrorCode.CONV_NOT_MEMBER, "operator not in conv " + convId);
        }
        if (member.getRole() > minRole) {
            throw new BizException(ErrorCode.CONV_PERMISSION_DENIED, "require role<=" + minRole);
        }
        return member;
    }

    /** 要求操作者是 OWNER */
    public ConversationMember requireOwner(long convId, long operatorId) {
        return requireRole(convId, operatorId, MemberRole.OWNER);
    }

    /** 要求操作者是 ADMIN 或 OWNER */
    public ConversationMember requireAdmin(long convId, long operatorId) {
        return requireRole(convId, operatorId, MemberRole.ADMIN);
    }

    /** 验证目标角色 > 操作者角色（不能对同级/上级操作） */
    public void verifyTargetNotHigher(long convId, long targetId, long operatorId) {
        ConversationMember target = getMember(convId, targetId);
        if (target == null) {
            throw new BizException(ErrorCode.CONV_MEMBER_NOT_FOUND, "target " + targetId);
        }
        ConversationMember operator = getMember(convId, operatorId);
        if (operator == null) {
            throw new BizException(ErrorCode.CONV_NOT_MEMBER, "operator " + operatorId);
        }
        if (target.getRole() <= operator.getRole()) {
            throw new BizException(ErrorCode.CONV_PERMISSION_DENIED, "cannot operate on same/higher role");
        }
    }

    public ConversationMember getMember(long convId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId)
                .eq(ConversationMember::getUserId, userId));
    }
}
```

---

## 11. Service 接口（`service/ConvService.java`，15 方法，与 ConvRpcService 对齐）

```java
package lanshan.manmu.conv.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.conv.*;

public interface ConvService {
    CreateConversationResp createConversation(CreateConversationReq req);
    ConversationDTO getConversation(long conversationId, long userId);
    ListConversationsResp listConversations(ListConversationsReq req);
    AddMembersResp addMembers(AddMembersReq req);
    void removeMembers(RemoveMembersReq req);
    GetMembersResp getMembers(GetMembersReq req);
    boolean isMember(long conversationId, long userId);
    PreCheckSendResp preCheckSend(PreCheckSendReq req);
    void markRead(MarkReadReq req);
    void updateLastMessage(UpdateLastMessageReq req);
    void muteMember(MuteMemberReq req);
    void transferOwner(TransferOwnerReq req);
    void updateAnnouncement(long convId, long operatorId, String content);
    GetSettingsResp getSettings(GetSettingsReq req);
    void updateSettings(UpdateSettingsReq req);
}
```

---

## 12. ServiceImpl（`service/impl/ConvServiceImpl.java`）

### 12.1 骨架 + 依赖注入

```java
package lanshan.manmu.conv.service.impl;

import lanshan.manmu.conv.mapper.*;
import lanshan.manmu.conv.model.entity.*;
import lanshan.manmu.conv.service.ConvService;
import lanshan.manmu.conv.util.PermissionChecker;
import lanshan.manmu.conv.util.UnreadCacheService;
import lanshan.manmu.conv.event.ConvEventPublisher;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ConvServiceImpl implements ConvService {

    private final ConversationMapper convMapper;
    private final ConversationMemberMapper memberMapper;
    private final ConvReadSeqMapper readSeqMapper;
    private final ConvSettingsMapper settingsMapper;
    private final SnowflakeIdWorker snowflake;
    private final PermissionChecker permissionChecker;
    private final UnreadCacheService unreadCache;
    private final ConvEventPublisher eventPublisher;

    @DubboReference
    private UserRpcService userRpcService;

    public ConvServiceImpl(ConversationMapper convMapper,
                           ConversationMemberMapper memberMapper,
                           ConvReadSeqMapper readSeqMapper,
                           ConvSettingsMapper settingsMapper,
                           SnowflakeIdWorker snowflake,
                           PermissionChecker permissionChecker,
                           UnreadCacheService unreadCache,
                           ConvEventPublisher eventPublisher) {
        this.convMapper = convMapper;
        this.memberMapper = memberMapper;
        this.readSeqMapper = readSeqMapper;
        this.settingsMapper = settingsMapper;
        this.snowflake = snowflake;
        this.permissionChecker = permissionChecker;
        this.unreadCache = unreadCache;
        this.eventPublisher = eventPublisher;
    }
}
```

### 12.2 关键方法实现要点

**createConversation**（`@Transactional`）：
1. 参数校验（单聊需 peerUserId 且不能是自己；群聊需 name 且不超长）
2. 单聊去重：`convMapper.findPrivateConversation(creatorId, peerUserId)`，命中则直接返回
3. Snowflake 生成 convId → insert Conversation（单聊 `ownerId=0`，群聊 `ownerId=creatorId`）
4. addMemberRecord：creator 为 OWNER（群聊）或 MEMBER（单聊）
5. 单聊：addMemberRecord(peerUserId, MEMBER)，memberCount=2
6. 群聊：批量 addMemberRecord(memberIds, MEMBER)，memberCount=1+size
7. **不发** `conversation.created` 事件（决策 20，Phase 1 无此事件类）

**addMembers**（`@Transactional`）：
1. `permissionChecker.requireAdmin(convId, operatorId)`
2. 校验 `MAX_MEMBER_COUNT(500)`
3. 逐个检查重复（`getMember` 返回 null 才 insert）
4. insert member 记录
5. 更新 conv.memberCount
6. **事务提交后**发 `MemberJoinedEvent` 事件（通过 `@TransactionalEventListener`，见 12.3）

**removeMembers**（`@Transactional`）：
1. 自退免校验；否则 `permissionChecker.requireAdmin(convId, operatorId)` + `verifyTargetNotHigher`
2. delete member 记录
3. 更新 conv.memberCount
4. **事务提交后**发 `MemberLeftEvent` 事件

**preCheckSend**：`getMember` 查成员身份（关键）→ 查 conv 填 convType/isMutedAll（非关键，错误只 log）→ 填 isMuted/muteUntil → 查全量 memberIds 供 message-service 扇出。**只收集信息不做拦截**。

**markRead**（`@Transactional`）：
1. `readSeqMapper.upsertReadSeq(snowflake.nextId(), convId, userId, lastReadSeq)`（UPSERT，GREATEST 保证只增不减）
2. **事务提交后**：`unreadCache.clearUnreadCount(userId, convId)` + 发 `ConversationReadUpdatedEvent` 事件

**transferOwner**（`@Transactional`）：
1. `permissionChecker.requireOwner(convId, operatorId)`
2. 不能转给自己 → `CONV_OWNER_TRANSFER_SELF`
3. 目标必须是成员 → `CONV_MEMBER_NOT_FOUND`
4. 更新 `conv.ownerId` → 新群主 `role=OWNER`，旧群主 `role=MEMBER`。**三步原子**（改进 MAIM 无事务缺陷）
5. **不发** `owner.transferred` 事件（决策 20）

**muteMember**（`@Transactional`）：`permissionChecker.requireAdmin` + `verifyTargetNotHigher` → 更新 `member.isMuted=true, muteUntil`

**updateAnnouncement**（`@Transactional`）：`permissionChecker.requireAdmin` + 长度校验 → 更新 `conv.announcement`

**updateLastMessage**（`@Transactional`）：
1. 查 conv（不存在仅 WARN 不抛，return）
2. **幂等更新**：`UPDATE conversations SET max_seq=#{seq}, ... WHERE id=#{convId} AND max_seq < #{seq}`
3. 被 Dubbo RPC 和 Kafka 消费者共同调用

**getConversation/listConversations/getMembers**：只读，批量调 `userRpcService.batchGetUserInfo` 补全成员信息，查 `conv_read_seqs` 拿已读位置，查 Redis 拿未读数。

> **spec-rev: ConversationDTO 新增 unreadCount 字段**：原 spec 12.2 要求 listConversations 查 Redis 未读数，但 ConversationDTO 无对应字段。已在 common 的 ConversationDTO 加 `unreadCount` 字段（long），由 `getConversation`/`listConversations` 查 Redis 填充。
>
> **各方法精确职责**（按 DTO 字段反推）：
> - **getConversation(convId, userId)**：查 Conversation → 转 DTO → 查 Redis `unreadCache.getUnreadCount` 填充 unreadCount → 返回。不查 read_seqs（DTO 无 lastReadSeq 字段）。
> - **listConversations(req)**：查用户所有会话（conv_members JOIN conversations，按 max_seq DESC 分页）→ 转 DTO 列表 → 批量查 Redis `unreadCache.batchGetUnread` 填充 unreadCount → 返回。
> - **getMembers(req)**：查会话成员（conv_members 分页）→ 查 conv_read_seqs 批量拿 lastReadSeq → 调 `userRpcService.batchGetUserInfo` 补全 username/avatar → 转 ConversationMemberDTO 列表。

**getSettings/updateSettings**：`settingsMapper.upsertSettings(...)`（UPSERT，COALESCE 处理 null=不更新）

### 12.3 事务后置操作（决策 10）

Redis DEL / Kafka send **不能放在 `@Transactional` 方法内**（外部系统失败会导致 DB 回滚）。用 Spring 事件机制解耦：

```java
// ConvServiceImpl 内部私有方法，只在事务内 publish Spring 事件
private void publishAfterCommit(Object springEvent) {
    ApplicationEventPublisherHolder.publish(springEvent);
}

// 例：markRead 事务内
@Transactional
public void markRead(MarkReadReq req) {
    readSeqMapper.upsertReadSeq(snowflake.nextId(), req.getConversationId(),
                                req.getUserId(), req.getLastReadSeq());
    // 不在这里调 unreadCache.clearUnreadCount 和 eventPublisher.publishReadUpdated
    // 改为发布 Spring 内部事件，由 @TransactionalEventListener 在 AFTER_COMMIT 处理
    publishAfterCommit(new MarkReadCompletedEvent(req.getUserId(), req.getConversationId(),
                                                   req.getLastReadSeq()));
}
```

```java
// event/ConvEventListener.java
package lanshan.manmu.conv.event;

import lanshan.manmu.conv.util.UnreadCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConvEventListener {

    private final UnreadCacheService unreadCache;
    private final ConvEventPublisher eventPublisher;

    public ConvEventListener(UnreadCacheService unreadCache, ConvEventPublisher eventPublisher) {
        this.unreadCache = unreadCache;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarkReadCompleted(MarkReadCompletedEvent evt) {
        // DB 事务已提交，此时执行外部系统操作；即使失败也不影响 DB 状态
        unreadCache.clearUnreadCount(evt.getUserId(), evt.getConvId());
        eventPublisher.publishReadUpdated(evt.getConvId(), evt.getUserId(), evt.getLastReadSeq());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersJoined(MembersJoinedEvent evt) {
        eventPublisher.publishMemberJoined(evt.getConvId(), evt.getUserIds(), evt.getJoinedBy());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembersLeft(MembersLeftEvent evt) {
        eventPublisher.publishMemberLeft(evt.getConvId(), evt.getUserIds(), evt.getRemovedBy());
    }
}
```

> **Spring 内部事件类**（放 `event/` 包，与 common 事件 DTO 区分）：
> - `MarkReadCompletedEvent`、`MembersJoinedEvent`、`MembersLeftEvent` —— 仅在 conv-service 内部使用，不序列化

---

## 13. RPC Provider 实现（`rpc/ConvRpcServiceImpl.java`）

`ConvRpcServiceImpl` 实现 common 的 `ConvRpcService` 接口，通过 `@DubboService` 暴露。**纯转发层**，不做业务逻辑，签名严格对齐接口（决策 13 已说明 `getConversation`/`isMember`/`updateAnnouncement` 用基本类型）。

```java
package lanshan.manmu.conv.rpc;

import lanshan.manmu.common.rpc.ConvRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.conv.service.ConvService;
import org.apache.dubbo.config.annotation.DubboService;
import lombok.extern.slf4j.Slf4j;

@DubboService
@Slf4j
public class ConvRpcServiceImpl implements ConvRpcService {

    private final ConvService convService;

    public ConvRpcServiceImpl(ConvService convService) {
        this.convService = convService;
    }

    @Override public CreateConversationResp createConversation(CreateConversationReq req) { return convService.createConversation(req); }
    @Override public ConversationDTO getConversation(long conversationId, long userId) { return convService.getConversation(conversationId, userId); }
    @Override public ListConversationsResp listConversations(ListConversationsReq req) { return convService.listConversations(req); }
    @Override public AddMembersResp addMembers(AddMembersReq req) { return convService.addMembers(req); }
    @Override public void removeMembers(RemoveMembersReq req) { convService.removeMembers(req); }
    @Override public GetMembersResp getMembers(GetMembersReq req) { return convService.getMembers(req); }
    @Override public boolean isMember(long conversationId, long userId) { return convService.isMember(conversationId, userId); }
    @Override public PreCheckSendResp preCheckSend(PreCheckSendReq req) { return convService.preCheckSend(req); }
    @Override public void markRead(MarkReadReq req) { convService.markRead(req); }
    @Override public void updateLastMessage(UpdateLastMessageReq req) { convService.updateLastMessage(req); }
    @Override public void muteMember(MuteMemberReq req) { convService.muteMember(req); }
    @Override public void transferOwner(TransferOwnerReq req) { convService.transferOwner(req); }
    @Override public void updateAnnouncement(long convId, long operatorId, String content) { convService.updateAnnouncement(convId, operatorId, content); }
    @Override public GetSettingsResp getSettings(GetSettingsReq req) { return convService.getSettings(req); }
    @Override public void updateSettings(UpdateSettingsReq req) { convService.updateSettings(req); }
}
```

> 与 `FileRpcServiceImpl` 风格完全对齐：构造器注入 `ConvService`，薄转发。

---

## 14. 事件发布器 ConvEventPublisher（`event/ConvEventPublisher.java`）

封装 Kafka 消息发送，**直接复用 common 已有事件类**（决策 8/20），**topic 用 `KafkaTopic.*` 常量**（决策 18）。Phase 1 用 String 序列化（JSON 字符串）。

```java
package lanshan.manmu.conv.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lanshan.manmu.common.constant.KafkaTopic;
import lanshan.manmu.common.event.ConversationReadUpdatedEvent;
import lanshan.manmu.common.event.MemberJoinedEvent;
import lanshan.manmu.common.event.MemberLeftEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConvEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ConvEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 成员加入：addMembers 成功后调用（实际由 ConvEventListener 在 AFTER_COMMIT 触发） */
    public void publishMemberJoined(long convId, List<Long> userIds, long joinedBy) {
        MemberJoinedEvent evt = new MemberJoinedEvent(convId, userIds, joinedBy);
        publish(KafkaTopic.CONVERSATION_MEMBER_JOINED, convId, evt);
    }

    /** 成员离开：removeMembers 成功后调用 */
    public void publishMemberLeft(long convId, List<Long> userIds, long removedBy) {
        MemberLeftEvent evt = new MemberLeftEvent(convId, userIds, removedBy);
        publish(KafkaTopic.CONVERSATION_MEMBER_LEFT, convId, evt);
    }

    /** 已读更新：markRead 成功后调用 */
    public void publishReadUpdated(long convId, long userId, long lastReadSeq) {
        ConversationReadUpdatedEvent evt = new ConversationReadUpdatedEvent(convId, userId, lastReadSeq);
        publish(KafkaTopic.CONVERSATION_READ_UPDATED, convId, evt);
    }

    private void publish(String topic, long key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(key), json);
            log.info("publish event topic={} key={} body={}", topic, key, json);
        } catch (JsonProcessingException e) {
            log.error("publish event failed topic={} key={}", topic, key, e);
            // 不抛异常：Kafka 发送失败不应影响已提交的 DB 事务
        }
    }
}
```

**Phase 1 发布的 Topic 清单**（全部用 `KafkaTopic.*` 常量）：

| Topic 常量 | 值 | 事件类 | 触发点 | 消费方 |
|-----------|----|--------|--------|--------|
| `KafkaTopic.CONVERSATION_MEMBER_JOINED` | `conversation.member.joined` | `MemberJoinedEvent` | addMembers | signaling-service、push |
| `KafkaTopic.CONVERSATION_MEMBER_LEFT` | `conversation.member.left` | `MemberLeftEvent` | removeMembers / 自退 | signaling-service、push |
| `KafkaTopic.CONVERSATION_READ_UPDATED` | `conversation.read.updated` | `ConversationReadUpdatedEvent` | markRead | signaling-service（同步对方已读状态） |

> **Phase 1 不发** `conversation.created` 和 `conversation.owner.transferred`（决策 20）。Phase 2 需先在 common 补 `ConversationCreatedEvent` / `OwnerTransferredEvent` 事件类，并在 `KafkaTopic` 加对应常量后再发。

---

## 15. Redis 未读计数服务 UnreadCacheService（`util/UnreadCacheService.java`）

只负责 **Clear**（markRead 时清零），**Incr/Decr 由 message-service 负责**（决策 6）。

```java
package lanshan.manmu.conv.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UnreadCacheService {

    private final StringRedisTemplate redis;
    private static final String KEY_PREFIX = "aim:unread:";

    public UnreadCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 清零某用户在某会话的未读数 */
    public void clearUnreadCount(long userId, long convId) {
        String key = KEY_PREFIX + userId + ":" + convId;
        redis.delete(key);
        log.debug("clear unread count userId={} convId={}", userId, convId);
    }

    /** 读取某用户在某会话的未读数（fallback 0） */
    public long getUnreadCount(long userId, long convId) {
        String val = redis.opsForValue().get(KEY_PREFIX + userId + ":" + convId);
        if (val == null) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 批量读取某用户所有会话的未读数（用于 listConversations） */
    public Map<Long, Long> batchGetUnread(long userId, Collection<Long> convIds) {
        List<String> keys = convIds.stream().map(id -> KEY_PREFIX + userId + ":" + id).toList();
        List<String> vals = redis.opsForValue().multiGet(keys);
        Map<Long, Long> result = new HashMap<>();
        int i = 0;
        for (Long convId : convIds) {
            String v = (vals == null || i >= vals.size()) ? null : vals.get(i);
            result.put(convId, v == null ? 0L : Long.parseLong(v));
            i++;
        }
        return result;
    }
}
```

> **未读计数生命周期**：message-service 发消息时 `INCR aim:unread:{userId}:{convId}`（对除发送者外的成员）；conv-service `markRead` 时 `DEL aim:unread:{userId}:{convId}`。

---

## 16. Kafka 消费者（`consumer/ConvMessageConsumer.java`）

conv-service 只消费 1 个 topic：`message.created`，用于触发 `updateLastMessage`。

**关键修正**（决策 17/22）：
- `@KafkaListener` **不指定 groupId**（由 Nacos 配置 `spring.kafka.consumer.group-id` 提供）
- `UpdateLastMessageReq` 用 `new` 构造（无 `@Builder`），字段名 `conversationId`/`lastMessageId`/`maxSeq`/`lastMessagePreview`
- 不再跨服务猜测 `content` 结构生成 preview（决策 22），直接透传 event 里的 `preview` 字段

```java
package lanshan.manmu.conv.consumer;

import lanshan.manmu.common.constant.KafkaTopic;
import lanshan.manmu.common.event.MessageCreatedEvent;
import lanshan.manmu.common.rpc.dto.conv.UpdateLastMessageReq;
import lanshan.manmu.conv.service.ConvService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class ConvMessageConsumer {

    private final ConvService convService;
    private final ObjectMapper objectMapper;

    public ConvMessageConsumer(ConvService convService, ObjectMapper objectMapper) {
        this.convService = convService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopic.MESSAGE_CREATED)
    // 注：不指定 groupId —— 由 Nacos 配置 spring.kafka.consumer.group-id: conv-service 提供（决策 17）
    public void onMessageCreated(ConsumerRecord<String, String> record) {
        try {
            MessageCreatedEvent evt = objectMapper.readValue(record.value(), MessageCreatedEvent.class);
            log.info("consume message.created convId={} seq={}", evt.getConvId(), evt.getSeq());

            // 用 new 构造（UpdateLastMessageReq 无 @Builder，字段名见 common 第 2.6 节）
            UpdateLastMessageReq req = new UpdateLastMessageReq(
                    evt.getConvId(),
                    evt.getMessageId(),
                    evt.getSeq(),
                    evt.getPreview()   // 由 message-service 生成（决策 22），conv-service 只透传
            );
            convService.updateLastMessage(req);
        } catch (Exception e) {
            log.error("consume message.created failed record={}", record.value(), e);
            // Phase 1 不做重试，等后续引入 DLQ（见 KafkaTopic.MESSAGE_CREATED_DLQ）
        }
    }
}
```

> **幂等性**：`updateLastMessage` 内部用 `WHERE max_seq < #{seq}` 条件更新，天然防重复消费。
> **DLQ**：Phase 2 引入 `KafkaTopic.MESSAGE_CREATED_DLQ` 死信队列（常量已在 common 定义）+ RetryTemplate。
> **前提**：common 的 `MessageCreatedEvent` 需先加 `preview` 字段（见第 2.5 节）。

---

## 17. 常量类 ConvConstants（`util/ConvConstants.java`）

**只保留 common 没有的常量**（决策 12）。角色用 `MemberRole.*`、会话类型用 `ConvType.*`，不重复定义。

```java
package lanshan.manmu.conv.util;

/**
 * conv-service 本地常量。
 * <p>角色用 {@link lanshan.manmu.common.constant.MemberRole}，会话类型用
 * {@link lanshan.manmu.common.constant.ConvType}，此处不重复定义。
 */
public final class ConvConstants {

    private ConvConstants() {}

    /** memberType：DB 存 'user'/'bot' */
    public static final String MEMBER_TYPE_USER = "user";
    public static final String MEMBER_TYPE_BOT  = "bot";

    /** 成员上限 */
    public static final int MAX_MEMBER_COUNT = 500;

    /** 字段长度限制 */
    public static final int MAX_NAME_LENGTH         = 32;
    public static final int MAX_ANNOUNCEMENT_LENGTH = 500;
    public static final int MAX_ALIAS_LENGTH        = 32;

    /** muteUntil 单位：epoch 秒（不是毫秒），0=永久或未禁言 */
    public static final long MUTE_PERMANENT = 0L;
}
```

**memberType 转换工具**（放在 `ConvServiceImpl` 私有方法或单独 util）：

```java
private static int toDtoType(String dbType) {
    return ConvConstants.MEMBER_TYPE_BOT.equals(dbType) ? 2 : 1;
}
private static String toDbType(int dtoType) {
    return dtoType == 2 ? ConvConstants.MEMBER_TYPE_BOT : ConvConstants.MEMBER_TYPE_USER;
}
```

> **不重复定义** `ROLE_OWNER/ADMIN/MEMBER`（用 `MemberRole.OWNER/ADMIN/MEMBER`）和 `TYPE_PRIVATE/GROUP`（用 `ConvType.SINGLE/GROUP`）。

---

## 18. 测试要点

### 18.1 单元测试（Service 层，Mock Mapper/Redis/Kafka）

| 测试类 | 覆盖方法 | 关键用例 |
|--------|---------|---------|
| `ConvServiceImplTest` | createConversation | 单聊去重命中→直接返回；单聊自己→BAD_REQUEST；群聊无 name→BAD_REQUEST；群聊 name 超长→BAD_REQUEST |
| | createConversation | 单聊→ownerId=0、memberCount=2；群聊→ownerId=creator、memberCount=1+n |
| | addMembers | 操作者非 ADMIN→PERMISSION_DENIED；超 500→MEMBER_LIMIT；成员已存在→跳过 insert；memberCount 自增正确 |
| | removeMembers | 自退免校验；非 ADMIN 踢人→PERMISSION_DENIED；踢同级/上级→PERMISSION_DENIED |
| | transferOwner | 非 OWNER→PERMISSION_DENIED；转给自己→OWNER_TRANSFER_SELF；目标非成员→MEMBER_NOT_FOUND；三步原子校验 |
| | muteMember | ADMIN 禁言 MEMBER 成功；ADMIN 禁言另一 ADMIN→PERMISSION_DENIED |
| | markRead | lastReadSeq 只增不减（UPSERT 用 GREATEST）；Spring 内部事件已发布（验证 unreadCache/eventPublisher 不在事务内调用） |
| | updateLastMessage | conv 不存在仅 log 不抛；seq < maxSeq 时跳过（幂等） |
| | preCheckSend | 非成员→isMember=false 仍返回（不拦截）；全员禁言→isMutedAll=true |
| | updateSettings | UPSERT COALESCE 语义：isMuted=null 不更新，isMuted=false 显式设为 false |
| `PermissionCheckerTest` | requireRole | 非成员→CONV_NOT_MEMBER；权限不足→PERMISSION_DENIED；权限足够→返回 member |
| | requireOwner/requireAdmin | OWNER 通过 ADMIN 通过 MEMBER 拒绝 |
| | verifyTargetNotHigher | 目标同级→PERMISSION_DENIED；目标下级→通过 |
| `ConvReadSeqMapperTest` | upsertReadSeq | 首次 insert；二次 update；GREATEST 保证只增不减 |
| `ConvSettingsMapperTest` | upsertSettings | 首次 insert；二次 update；COALESCE null 语义正确 |
| `UnreadCacheServiceTest` | clearUnreadCount | redis.delete 被调用 |
| | batchGetUnread | multiGet 返回值正确映射；null 值 fallback 0 |

### 18.2 集成测试（@SpringBootTest + Testcontainers）

| 场景 | 验证点 |
|------|--------|
| 单聊端到端 | A 创建→B 入群→A 发消息→B markRead→Redis 清零→已读位置 UPSERT |
| 群聊端到端 | A 创建群→拉 B、C→A 转让给 B→A 变 MEMBER, B 变 OWNER→A 自退 |
| 单聊去重 | A↔B 第二次创建返回同一 convId |
| Kafka 链路 | 发送 MessageCreatedEvent（含 preview 字段）→ conv-service 消费 → conversations.max_seq 更新 |
| 事务后置 | markRead 抛异常 → Redis 未清零、Kafka 未发事件（事务回滚）；markRead 成功 → Redis/Kafka 在提交后执行 |
| 事务回滚 | addMembers 中 insert 部分成员后抛异常 → memberCount 不变、已 insert 的成员回滚、未发 MemberJoinedEvent |

### 18.3 Testcontainers 配置

```java
@Testcontainers
@SpringBootTest
abstract class ConvIntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aim").withUsername("postgres").withPassword("postgres")
            .withInitScript("schema-conv.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

> **Dubbo Reference Mock**：`@DubboReference UserRpcService` 在测试中需 mock，避免连真实 Nacos 找 Provider。可用 `@MockBean` 替换：
> ```java
> @MockBean
> private UserRpcService userRpcService;
> ```

---

## 19. 实施顺序

### Phase 1.0：common 改动（前置）
1. 删除 3 个孤儿 DTO（决策 13，第 2.3 节）
2. 给 `MessageCreatedEvent` 加 `preview` 字段（决策 22，第 2.5 节）
3. **验证点**：`mvn -pl common compile` 通过

### Phase 1.1：脚手架（先跑起来）
1. 创建包结构（第 6.2 节）：`config/consumer/mapper/model/{dto,entity}/event/rpc/service/impl/util`
2. 更新 `conv-service/pom.xml`（新增 5 个依赖，第 3 节）
3. 更新 `conv-service/src/main/resources/application.yml`（第 4 节模板，只放 Nacos 连接 + 触发拉取）
4. 更新 `docs/sql/init/nacos-init-data.sql`（追加 conv_svc_content 变量 + INSERT，第 5.2 节）
5. 执行 `psql -f docs/sql/init/nacos-init-data.sql && docker compose restart nacos`
6. 写启动类 + 4 个 Entity + 4 个 Mapper
7. 写 `MybatisPlusConfig`、`SnowflakeConfig`（不加 @RefreshScope）、`KafkaProducerConfig`
8. **验证点**：`mvn -pl conv-service spring-boot:run` 启动成功；Nacos 控制台可见 conv-service.yml DataId；服务注册成功（无 401）；DB 连接成功

### Phase 1.2：核心写路径
1. 实现 `util/PermissionChecker`（复用 `MemberRole.*`）+ `util/ConvConstants`
2. 实现 `service/ConvService` 接口 + `service/impl/ConvServiceImpl` 骨架
3. 实现 `createConversation`（含单聊去重）+ 单测
4. 实现 `addMembers` / `removeMembers` + 单测
5. 实现 `muteMember` / `transferOwner` / `updateAnnouncement` + 单测
6. **验证点**：所有写路径单测通过，事务回滚测试通过

### Phase 1.3：核心读路径
1. 实现 `getConversation` / `listConversations` / `getMembers`
2. 对接 `UserRpcService.batchGetUserInfo` 补全成员信息
3. 对接 Redis 读未读数（`UnreadCacheService.getUnreadCount` / `batchGetUnread`）
4. **验证点**：返回 DTO 字段完整，未读数正确

### Phase 1.4：消息链路 + 事务后置
1. 实现 `preCheckSend`（被 message-service 调用）
2. 实现 `updateLastMessage`（被 Dubbo + Kafka 双入口调用，幂等 `WHERE max_seq < #{seq}`）
3. 实现 `markRead` + `ConvReadSeqMapper.upsertReadSeq` + `ConvSettingsMapper.upsertSettings`
4. 实现 `event/ConvEventPublisher` + `event/ConvEventListener`（`@TransactionalEventListener(AFTER_COMMIT)`）
5. **验证点**：发消息→Last Message 更新；已读→未读清零；事务回滚时 Redis/Kafka 不执行

### Phase 1.5：Kafka 消费 + RPC 暴露
1. 实现 `consumer/ConvMessageConsumer`（`@KafkaListener` 不指定 groupId，透传 preview）
2. 实现 `rpc/ConvRpcServiceImpl` 暴露 15 个方法（签名严格对齐 ConvRpcService 接口）
3. **验证点**：其他服务可通过 Dubbo 正常调用；Kafka 事件正常发送和消费

### Phase 1.6：集成测试
1. Testcontainers 起容器跑端到端
2. 修复边界 case
3. **验证点**：所有集成测试通过

---

## 20. 风险与遗留

| # | 风险点 | 应对 |
|---|--------|------|
| 1 | Kafka 消费失败无重试 | Phase 1 接受，Phase 2 引入 DLQ（`KafkaTopic.MESSAGE_CREATED_DLQ`）+ RetryTemplate |
| 2 | `UserRpcService` 不可用时降级策略缺失 | Phase 1 直接抛异常，Phase 2 加 fallback 返回精简 DTO |
| 3 | 单聊去重 SQL 在大表上性能未验证 | Phase 1 依赖 `idx_conv_members_pair` 索引，Phase 2 监控慢查询 |
| 4 | 未读计数双写（Redis + DB）一致性 | markRead 时 Redis DEL 在事务提交后执行，存在短暂不一致窗口，业务可接受；Phase 2 可加定时对账 |
| 5 | `conv_bots` 表预留但未使用 | Phase 2 引入 AI 角色时启用，需补充 `BotRpcService` 接口 |
| 6 | Phase 1 不发 `conversation.created`/`owner.transferred` 事件 | signaling-service 端的「会话创建推送」和「群主变更推送」暂缺，Phase 2 在 common 补事件类后补发 |
| 7 | `UserRpcService.batchGetUserInfo` 已有批量接口 | 第 2.6 节字段已确认可用，无需 N+1（与 file-service 不同） |
| 8 | 事务后置失败（Redis/Kafka 在 AFTER_COMMIT 出错） | DB 状态正确但外部系统未同步；Phase 1 仅 log，Phase 2 引入 outbox 表 + 补偿任务 |
| 9 | `MessageCreatedEvent.preview` 字段需 message-service 配合生成 | Phase 1 接受 null preview（存空字符串），Phase 2 推动 message-service 实现 preview 生成逻辑 |

---

## 21. 与 MAIM 的差异对照

| 维度 | MAIM（Go） | AIM（Java） | 改进点 |
|------|-----------|-------------|--------|
| 事务 | createConversation 无事务 | 全部 `@Transactional` | 数据一致性 |
| 事务后置 | goroutine 随意并发 | `@TransactionalEventListener(AFTER_COMMIT)` | 外部系统不污染 DB 事务 |
| 权限校验 | 散落在各 handler | 统一 `PermissionChecker` + 复用 `MemberRole.*` | DRY |
| 错误处理 | 直接 return err | `BizException + ErrorCode + detail` | 错误码可追溯 |
| 事件发布 | NSQ + 自定义结构 | Kafka + common 事件类（snake_case JSON）+ `KafkaTopic.*` 常量 | 生态统一 + DRY |
| 配置管理 | 本地配置文件 | Nacos DataId + 本地兜底 | 单一配置源 |
| 配置校验 | 校验逻辑散落 | Service 层集中 + `BAD_REQUEST + detail` | 一致性 |
| UPSERT | select-then-update（有竞态） | PG 原生 `ON CONFLICT DO UPDATE` + `GREATEST`/`COALESCE` | 并发安全 |
| 未读计数 | conv-service 全管 | conv 只 Clear，message 负责 Incr | 职责单一 |
| 系统消息 | goroutine 异步 | Dubbo 同步 | 无并发原语 |
| PreCheckSend | 拦截型（错误即拒） | 信息收集型（不拦截） | 关注点分离 |
| preview 生成 | conv-service 跨服务猜 content 结构 | message-service 生成，conv-service 只透传 | 职责边界 |
| Kafka 常量 | 魔法字符串 | `KafkaTopic.*` / `KafkaGroup.*` 常量 | DRY |
| 实体注解 | 无 ORM | `@Data + @NoArgsConstructor + @AllArgsConstructor + @TableName + @TableId`，Boolean 加 `@TableField` | 与 user/file-service 对齐 |
| Snowflake | 启动期固定 | 启动期固定，**不加 @RefreshScope**（防 ID 重复） | 安全性 |

---

> **Spec 终**。本文档严格遵循 [nacos-config-spec.md](./nacos-config-spec.md) 配置规范，与 `user-service` / `file-service` 代码风格对齐（实体注解、包结构、Service 接口/实现分离、PermissionChecker 模式），作为 conv-service 实现的唯一蓝图。开发过程中如发现设计缺陷，需回本文档修订并同步 commit message 中标注 `spec-rev: <reason>`。
