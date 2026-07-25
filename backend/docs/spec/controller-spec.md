# Controller 层 spec（架构 1：双协议暴露）

> **目标**：为 `user-service` / `conv-service` / `file-service` 三个微服务补全 HTTP Controller 层，使其同时暴露 HTTP（供前端）+ Dubbo（供其他业务服务）。
> **架构决策**：采用架构 1（双协议暴露）—— 业务服务自己暴露 HTTP Controller，gateway 纯路由 + JWT 鉴权。
> **适用范围**：本 spec 是三个服务 Controller 层的总纲，各 service-spec.md 只追加"新增 controller 包"的小改。
> **spec-rev 背景**：原 conv-service-spec / file-service-spec 设计为"纯 Dubbo Provider，不暴露 HTTP"，与前后端联调需求冲突，按 spec 缺陷修复流程修订。

---

## 0. 设计决策清单

| # | 决策项 | 值 | 理由 |
|---|---|---|---|
| 1 | 架构选型 | 架构 1（双协议暴露） | 大厂最主流，与 AGENTS.md 反模式"网关不写业务逻辑"一致 |
| 2 | JWT 鉴权位置 | 网关统一鉴权 | 下游服务无需 JWT 密钥；内网可信 |
| 3 | userId 传递方式 | HTTP header（`X-User-Id`） | 网关已鉴权，header 简单可读 |
| 4 | API 路径前缀 | Controller 自带 `/api/v1/`，网关不 strip | 与 API 文档完全对齐 |
| 5 | 全局异常处理 | 每个服务一个 `@RestControllerAdvice` | 服务自治，不抽到 common |
| 6 | 大整数序列化 | Jackson 配置 + 前端 JSONbig | 复用前端现有方案 |
| 7 | 端口分配 | gateway=9080, user=8081, conv=8082, file=8083 | 顺序分配，无冲突 |
| 8 | 网关 Redis | 黑名单查询 | 复用 user-service 现有 `revoked_token:{jti}` 机制 |
| 9 | 白名单路径 | `/api/v1/auth/login`, `/register`, `/refresh`, `/public/**` | 与前端 client.ts WHITE_LIST 对齐 |
| 10 | 网关不写业务逻辑 | 严格遵守 AGENTS.md 反模式 | gateway 只做路由 + 鉴权 |

---

## 1. 架构定位

### 1.1 链路总览
```
前端 → gateway-service(9080, SCG + JwtAuthGlobalFilter)
         ↓ 注入 X-User-Id / X-Device-Id / X-Platform
       按路径路由到下游
         ↓
       user-service(8081) / conv-service(8082) / file-service(8083)
         ↓ Controller 从 header 取 userId
       本地 Service（业务逻辑）
         ↓ 跨服务调用时
       @DubboReference 其他服务
```

### 1.2 各角色职责
| 角色 | 职责 | 不做 |
|---|---|---|
| gateway-service | 路由转发、JWT 鉴权、header 注入 | 业务逻辑、参数校验、数据库访问 |
| 业务服务 Controller | HTTP 入口、参数组装、调 Service | 业务逻辑（透传给 Service） |
| 业务服务 Service | 业务逻辑、事务、DB 访问 | HTTP 相关代码 |
| 业务服务 RpcServiceImpl | Dubbo Provider 薄转发 | 业务逻辑 |

### 1.3 与 AGENTS.md 反模式对齐
- ✅ "gateway 只做路由转发、JWT 鉴权、限流" — 本 spec 严格遵守
- ✅ "服务间调用走 Dubbo RPC 或 Kafka" — Controller 内部跨服务调用走 `@DubboReference`
- ✅ "统一响应 Result<T>" — Controller 返回 `Result<T>`
- ✅ "业务错误抛 BizException" — Service 层抛 BizException，Controller 不 catch

---

## 2. 端口与路径前缀

### 2.1 端口分配
| 服务 | HTTP 端口 | Dubbo 端口 |
|---|---|---|
| gateway-service | 9080 | — |
| user-service | 8081 | 20881 |
| conv-service | 8082 | 20883 |
| file-service | 8083 | 20884 |

### 2.2 路径前缀策略
- **前端调用**：`http://{host}:9080/api/v1/{资源}`（与 [api-v1.md](../API/api-v1.md) Base URL 一致）
- **网关路由**：按 `Path=/api/v1/{service}/**` 路由，**网关不 strip 前缀**
- **Controller 路径**：`@RequestMapping("/api/v1/{资源}")`，与 API 文档完全对齐

### 2.3 网关路由表
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**
        - id: conv-service
          uri: lb://conv-service
          predicates:
            - Path=/api/v1/convs/**
        - id: file-service
          uri: lb://file-service
          predicates:
            - Path=/api/v1/files/**
```

---

## 3. JWT 网关统一鉴权

### 3.1 鉴权流程
```
前端 → gateway-service
         ↓
   JwtAuthGlobalFilter（全局过滤器，order=-100）
         ↓ 1. 白名单路径直接放行
         ↓ 2. 提取 Authorization: Bearer <token>
         ↓ 3. JWT 签名校验（Hutool JWT）+ 过期校验
         ↓ 4. Redis 黑名单查询（revoked_token:{jti}）
         ↓ 5. 注入 header: X-User-Id / X-Device-Id / X-Platform
         ↓
   路由到下游服务
         ↓
   Controller 从 @RequestHeader 取 userId
```

### 3.2 白名单路径
与 [前端 client.ts WHITE_LIST](../../frontend/src/apis/client.ts) 对齐：
- `/api/v1/auth/login`
- `/api/v1/auth/register`
- `/api/v1/auth/refresh`（用 RefreshToken，不是 AccessToken）
- `/api/v1/public/**`

### 3.3 header 协议
网关校验通过后注入以下 header，下游服务必须信任（内网环境）：

| Header | 类型 | 说明 |
|---|---|---|
| `X-User-Id` | long | 当前用户 ID |
| `X-Device-Id` | String | 当前设备 ID |
| `X-Platform` | String | 客户端平台（web/ios/android） |

### 3.4 网关依赖
gateway-service 需新增：
- `spring-boot-starter-data-redis`（黑名单查询）
- `cn.hutool:hutool-all`（JWT 工具，与 user-service 风格对齐）
- `spring-cloud-starter-alibaba-nacos-config`（拉取 JWT 密钥等配置）

JWT 密钥与 user-service 共享（从 Nacos `COMMON_GROUP/application.yml` 读取 `aim.jwt.secret`）。

### 3.5 下游服务配置
- `server.forward-headers-strategy: framework`：让 Spring MVC 信任网关注入的 header
- Controller 从 `@RequestHeader("X-User-Id") long userId` 取值
- **不**在下游做 JWT 二次校验（YAGNI）

---

## 4. 通用约定

### 4.1 统一响应格式
所有 Controller 返回 `Result<T>`（common 模块）：
```java
@PostMapping("/api/v1/convs")
public Result<CreateConversationResp> create(@RequestBody CreateConversationReq req,
                                              @RequestHeader("X-User-Id") long userId) {
    req.setCreatorId(userId); // 网关已鉴权，信任 header
    return Result.ok(convService.createConversation(req));
}
```

### 4.2 全局异常处理
每个服务一个 `@RestControllerAdvice`，放 `controller/advice/` 包：

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException ex) {
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("unexpected error", ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
```

**不抽到 common**：每个服务异常处理可能略有差异（如 file-service 处理 MultipartException），保持自治。

### 4.3 大整数序列化
前端 [client.ts](../../frontend/src/apis/client.ts) 已用 `JSONbig.parse`，后端 Jackson 配置：
```yaml
spring:
  jackson:
    serialization:
      write-bigdecimal-as-plain: true
```

long 类型字段（userId/messageId/conversationId）直接序列化为数字，前端 JSONbig 自动转 BigInt。

### 4.4 Controller 包结构
每个服务新增 `controller/` 包：
```
xxx-service/src/main/java/lanshan/manmu/xxx/
├── controller/
│   ├── XxxController.java        # REST 接口
│   └── advice/
│       └── GlobalExceptionHandler.java  # @RestControllerAdvice
```

### 4.5 Controller 编码规范
- 构造器注入 Service（与 AGENTS.md "统一使用构造器注入"一致）
- 不写业务逻辑，只做参数组装 + 调 Service
- 路径参数用 `@PathVariable`，请求体用 `@RequestBody`，header 用 `@RequestHeader`
- 不 catch 业务异常（交给 `@RestControllerAdvice`）
- 不返回 `ResponseEntity`，直接返回 `Result<T>`

---

## 5. user-service Controller

### 5.1 接口列表
对齐 [api-v1.md §2-§3](../API/api-v1.md)：

| 方法 | 路径 | 鉴权 | 调用 Service 方法 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ 白名单 | `userService.register(req)` |
| POST | `/api/v1/auth/login` | ❌ 白名单 | `userService.login(req)` |
| POST | `/api/v1/auth/logout` | ✅ | `userService.logout(accessToken, refreshToken)` |
| GET | `/api/v1/auth/validate` | ✅ | `userService.validateToken(accessToken)` |
| POST | `/api/v1/auth/refresh` | ❌ 白名单 | `userService.refreshToken(refreshToken)` |
| GET | `/api/v1/users/me` | ✅ | `userService.getUserInfo(userId)` |
| PUT | `/api/v1/users/me` | ✅ | `userService.updateProfile(userId, req)` |
| PUT | `/api/v1/users/me/password` | ✅ | `userService.updatePassword(userId, old, new)` |
| GET | `/api/v1/users/{userId}` | ✅ | `userService.getUserInfo(userId)` |
| POST | `/api/v1/users/batch` | ✅ | `userService.batchGetUserInfo(userIds)` |
| POST | `/api/v1/users/search` | ✅ | `userService.searchUsers(keyword, pageNum, pageSize)` |

### 5.2 文件清单
- `user-service/src/main/java/lanshan/manmu/user/controller/AuthController.java`
- `user-service/src/main/java/lanshan/manmu/user/controller/UserController.java`
- `user-service/src/main/java/lanshan/manmu/user/controller/advice/GlobalExceptionHandler.java`

### 5.3 特殊处理
- `register` / `login` / `refresh` 不需要 `X-User-Id` header（白名单）
- `logout` 从 `Authorization` header 取 accessToken，从 body 取 refreshToken
- `validate` 从 `Authorization` header 取 accessToken
- `users/me` 系列从 `X-User-Id` 拿当前用户
- `users/{userId}` 用 path 参数
- `users/batch-status`（在线状态）**暂不实现**（属 signaling-service 域）

---

## 6. conv-service Controller

### 6.1 接口列表
对齐 [api-v1.md §5](../API/api-v1.md)：

| 方法 | 路径 | 调用 Service 方法 |
|---|---|---|
| POST | `/api/v1/convs` | `convService.createConversation(req)` |
| GET | `/api/v1/convs/{conversationId}` | `convService.getConversation(convId, userId)` |
| GET | `/api/v1/convs` | `convService.listConversations(req)` |
| POST | `/api/v1/convs/{conversationId}/members/invite` | `convService.addMembers(req)` |
| POST | `/api/v1/convs/{conversationId}/members/kick` | `convService.removeMembers(req)` |
| GET | `/api/v1/convs/{conversationId}/members` | `convService.getMembers(req)` |
| PUT | `/api/v1/convs/{conversationId}/members/{userId}/mute` | `convService.muteMember(req)` |
| DELETE | `/api/v1/convs/{conversationId}/members/{userId}/mute` | `convService.muteMember(req)`（duration=0） |
| POST | `/api/v1/convs/{conversationId}/transfer` | `convService.transferOwner(req)` |
| PUT | `/api/v1/convs/{conversationId}/announcement` | `convService.updateAnnouncement(...)` |
| DELETE | `/api/v1/convs/{conversationId}/announcement` | `convService.updateAnnouncement(..., "")` |
| GET | `/api/v1/convs/{conversationId}/settings` | `convService.getSettings(req)` |
| PUT | `/api/v1/convs/{conversationId}/settings` | `convService.updateSettings(req)` |
| PUT | `/api/v1/convs/{conversationId}/read` | `convService.markRead(req)` |

### 6.2 文件清单
- `conv-service/src/main/java/lanshan/manmu/conv/controller/ConvController.java`
- `conv-service/src/main/java/lanshan/manmu/conv/controller/advice/GlobalExceptionHandler.java`

### 6.3 暂不暴露的接口
属其他服务域或 Phase 2 范围：
- `DELETE /convs/{conversationId}`（解散会话）— Phase 2
- `PUT /convs/{conversationId}/info`（更新群信息）— Phase 2
- `PUT /convs/{conversationId}/members/{userId}/role`（设置管理员）— spec 未定义
- `POST/DELETE /convs/{conversationId}/mute-all`（全员禁言）— spec 未定义
- `GET /convs/{conversationId}/read-status/{messageId}`（已读状态）— 属 message-service 域

---

## 7. file-service Controller

### 7.1 接口列表
对齐 [api-v1.md §7](../API/api-v1.md)：

| 方法 | 路径 | 调用 Service 方法 |
|---|---|---|
| POST | `/api/v1/files/upload-url` | `fileService.getUploadURL(req)` |
| POST | `/api/v1/files/confirm` | `fileService.confirmUpload(req)` |
| GET | `/api/v1/files/{fileId}/download` | `fileService.getDownloadURL(req)` |
| GET | `/api/v1/files/{fileId}/info` | `fileService.getFileInfo(fileId, userId)` |
| DELETE | `/api/v1/files/{fileId}` | `fileService.deleteFile(fileId, userId)` |
| POST | `/api/v1/files/batch` | `fileService.batchGetFileInfo(fileIds, userId)` |

### 7.2 文件清单
- `file-service/src/main/java/lanshan/manmu/file/controller/FileController.java`
- `file-service/src/main/java/lanshan/manmu/file/controller/advice/GlobalExceptionHandler.java`

---

## 8. 网关改造

### 8.1 新增文件
- `gateway-service/src/main/java/lanshan/manmu/gateway/filter/JwtAuthGlobalFilter.java`

### 8.2 JwtAuthGlobalFilter 实现要点
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/public/"
    );

    private final StringRedisTemplate redis;
    private final byte[] jwtSecretBytes;  // @Value("${aim.jwt.secret}")

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 1. 白名单放行
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        // 2. 提取 Bearer token
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing token");
        }
        String token = auth.substring(7);
        try {
            // 3. JWT 校验
            JWT jwt = JWT.of(token).setKey(jwtSecretBytes);
            if (!jwt.verify()) return unauthorized(exchange, "invalid signature");
            if (jwt.isExpired()) return unauthorized(exchange, "token expired");
            // 4. Redis 黑名单
            String jti = (String) jwt.getPayload("jti");
            Boolean hasKey = redis.hasKey("revoked_token:" + jti);
            if (Boolean.TRUE.equals(hasKey)) return unauthorized(exchange, "token revoked");
            // 5. 注入 header
            long userId = Long.parseLong(jwt.getPayload("userId").toString());
            String deviceId = (String) jwt.getPayload("deviceId");
            String platform = (String) jwt.getPayload("platform");
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Device-Id", deviceId)
                    .header("X-Platform", platform)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            log.warn("jwt auth failed: {}", ex.getMessage());
            return unauthorized(exchange, "auth failed");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        // 返回 401 + Result.fail(UNAUTHORIZED) JSON
    }

    @Override
    public int getOrder() { return -100; }
}
```

### 8.3 gateway-service 配置
见 plan 文件第六章 6.3 节完整 application.yml。

---

## 9. 配置变更清单

### 9.1 各服务 pom.xml 新增依赖
user-service / conv-service / file-service 都加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 9.2 各服务 application.yml 新增
- user-service：已有 `server.port: 8081`，仅补 jackson + forward-headers
- conv-service 新增：
  ```yaml
  server:
    port: ${SERVER_PORT:8082}
    forward-headers-strategy: framework
  spring:
    jackson:
      serialization:
        write-bigdecimal-as-plain: true
  ```
- file-service 新增（同 conv-service，端口 8083）

### 9.3 注释修订
conv-service / file-service 原 application.yml 注释"纯 Dubbo Provider，不对外提供 HTTP 端口"改为：
```yaml
# 业务服务同时暴露 HTTP（8082/8083）+ Dubbo Provider
# HTTP 供 gateway 转发前端请求；Dubbo 供其他业务服务 RPC 调用
```

### 9.4 Nacos DataId 调整
- `conv-service.yml`：保持不变（业务配置不动）
- `file-service.yml`：保持不变
- `gateway-service.yml`：新增（JWT 配置 + Redis + 路由）— 本次新增
- `COMMON_GROUP/application.yml`：加 `aim.jwt.secret` 与 `spring.jackson.serialization.write-bigdecimal-as-plain: true`

---

## 10. 验证标准

### 10.1 编译验证
```bash
mvn compile -pl common,gateway-service,user-service,conv-service,file-service -am
```

### 10.2 单测验证
```bash
mvn test -pl user-service,conv-service,file-service
```
- conv-service 现有 95 个测试必须全通过（不能因加 web 依赖破坏）
- 三个服务新增 Controller 单测（MockMvc）

### 10.3 启动验证
- 三个服务启动日志含 `Tomcat started on port 808x`
- 三个服务注册到 Nacos（HTTP + Dubbo 都注册）
- gateway-service 启动日志含路由加载

### 10.4 接口验证（curl）
```bash
# 注册
curl -X POST http://localhost:9080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"Abc@123456","deviceId":"d1","platform":"web"}'

# 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:9080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"test","password":"Abc@123456","deviceId":"d1","platform":"web"}' | jq -r '.data.tokens.accessToken')

# 创建会话（带 token，网关注入 X-User-Id）
curl -X POST http://localhost:9080/api/v1/convs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":1,"peerUserId":123456}'

# 拉会话列表
curl http://localhost:9080/api/v1/convs -H "Authorization: Bearer $TOKEN"
```

### 10.5 鉴权验证
- 无 token 访问 `/api/v1/convs` → 401
- 带无效 token → 401
- 带过期 token → 401
- 带黑名单 token → 401
- 带合法 token → 200 + 正确响应

---

## 11. spec-rev 关联

本 spec 落地时需同步修订：
- `conv-service-spec.md` L238/L251/L434：移除"不需要 web / 无 controller 包 / 不暴露 HTTP"
- `file-service-spec.md` L227：移除"不需要 web"
- `nacos-config-spec.md` L40/L69/L130：移除"纯 Dubbo Provider 则删掉 server.port"

修订 commit message 标注 `spec-rev: 架构 1 双协议暴露`。
