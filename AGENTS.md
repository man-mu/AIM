# AGENTS.md

## 语言

- 写文档、总结、计划、问题澄清时优先中文；代码标识符和命令保持原文。

## 工作流

- 后端代码更改后必须执行 `mvn compile` 验证编译通过；涉及测试的改动须 `mvn test` 通过。
- 前端代码更改后必须执行 `pnpm typecheck` 验证类型通过；涉及测试的改动须 `pnpm test` 通过。
- 任务完成后总结工作区改动并 `git commit`，message 必须符合 [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) 规范（如 `feat:` / `fix:` / `docs:` / `refactor:`）。
- 任务完成后必须清理临时文件，不要将它们留在工作目录或提交到版本控制。

## 接口契约

**唯一契约文档**：`API/api-v1.md`（覆盖 IM 基础功能八域：Auth / User / Friend / Conversation / Message / File / Notification / WebSocket + 通用约定 + 错误码全表）。

- **开发前必读**：任何涉及对外 HTTP 接口或 WebSocket 协议的开发/修复任务，动手前必须先阅读 `API/api-v1.md` 对应章节，实现必须符合契约。
- **同步义务**：实现偏离契约（路径/字段/错误码/行为语义）时，**必须同时更新契约文档**——实现与文档进同一个 commit，禁止只改代码不留文档。
- **前端 mock 同源**：前端 `frontend/src/mocks/handlers/` 的行为即契约（mock 先行），后端按契约实现；前端同学改 mock 契约时须回写文档。
- **错误码**：HTTP 错误码定义在 `backend/common/.../exception/ErrorCode.java`，编号与文案以契约第 10 章为准，新增错误码须同步文档。
- **WebSocket 事件**：事件名以契约第 9 章为准，后端常量在 `common/.../constant/WsEvent.java`，前端在 `frontend/src/realtime/protocol.ts`，改动必须双向同步 + 更新文档。

## 后端开发约束

### 技术栈

Java 21 + Spring Boot 3.5 + Dubbo 3（服务间 RPC）+ Spring Cloud Gateway（HTTP 网关）+ MyBatis-Plus + PostgreSQL + Kafka + Redis + Nacos（注册/配置中心）+ MinIO（对象存储）。

### 结构

```text
aim/backend/
├── pom.xml                   # 父 POM（聚合 + BOM 依赖版本管理）
├── common/                   # 共享模块（Dubbo 接口、常量、事件 DTO、异常、工具；保持纯接口/DTO 轻量）
├── gateway-service/          # REST API 网关（Spring Cloud Gateway：路由 + JWT 鉴权 + 限流）
├── ws-gateway-service/       # WebSocket 网关（Netty + 内部 Dubbo 推送）
├── user-service/             # 用户服务（Dubbo Provider）
├── friend-service/           # 好友服务（Dubbo Provider）
├── conv-service/             # 会话群组服务（Dubbo Provider）
├── message-service/          # 消息引擎（Dubbo + Kafka + Outbox）
├── signaling-service/        # 信令推送（Kafka Consumer + Fanout）
├── file-service/             # 文件管理（MinIO）
├── bot-service/              # Phase 2: Bot 管理 + AI 执行（目录占位）
├── kb-service/               # Phase 2: 知识库 RAG（目录占位）
├── llm-gateway-service/      # Phase 2: LLM 模型网关（目录占位）
└── docker-compose.yml        # 本地基础设施（PG/Redis/Kafka/Nacos/MinIO）
```

每个微服务模块内部结构：

```text
xxx-service/
└── src/main/java/lanshan/manmu/<module>/
    ├── XxxServiceApplication.java   # 启动类
    ├── config/                        # 配置类
    ├── rpc/                           # Dubbo 服务实现（@DubboService）
    ├── service/                       # 业务逻辑层
    │   └── impl/
    ├── mapper/                        # MyBatis-Plus Mapper
    ├── model/
    │   ├── entity/                    # 数据库实体
    │   ├── dto/                        # 请求/响应 DTO
    │   └── event/                     # Kafka 事件对象
    ├── consumer/                      # Kafka 消费者
    └── dispatcher/                    # Outbox 轮询器（message-service 专属）
```

### 约定

- **Dubbo 接口定义**：服务接口（Java 原生 interface）统一放在 `common` 模块的 `rpc/` 包下，各服务通过 `@DubboService` 暴露、`@DubboReference` 引用。
- **统一响应**：对外 API 返回 `Result<T>`（`common` 模块 `result/` 包），业务错误抛出 `BizException`（`common` 模块 `exception/` 包）。
- **Kafka 事件**：事件 DTO 统一定义在 `common` 模块 `event/` 包下，生产者和消费者共用同一类型，Topic 常量定义在 `common` 模块 `constant/` 包下。
- **版本管理**：所有依赖版本由父 POM 的 `dependencyManagement` 统一锁定，子模块 `pom.xml` 不声明版本号。
- **数据库**：每个服务只访问自己的数据库 schema，不跨服务直连数据库；不使用 Flyway，SQL 手动管理。
- **服务间调用**：服务间通信走 Dubbo RPC 或 Kafka 异步事件，不在业务层直接 HTTP 调用。
- **消息推送**：所有实时推送必须经 `signaling-service` 扇出 → `ws-gateway-service` 投递，其他服务不直接调用 `ws-gateway-service` 的推送接口。
- **网关职责**：`gateway-service` 只做路由转发、JWT 鉴权、限流，不写业务逻辑。
- **配置管理**：各服务 `application.yml` 只含基础配置，业务配置通过 Nacos 配置中心管理。
- **Phase 2 隔离**：Phase 1 阶段不引入任何 Spring AI / SAA 依赖，`bot-service` / `kb-service` / `llm-gateway-service` 仅保留目录占位，不加入父 POM modules。
- **依赖注入**：统一使用构造器注入，禁止字段注入（`@Autowired` 或 `@Resource` 直接标注于字段）。配置值 (`@Value`) 也应通过构造器参数注入，保持不可变性和可测试性。
- **日志记录**：使用 slf4j 记录关键操作、异常信息等，便于调试和监控系统运行。

### 反模式

- 不要在子模块 `pom.xml` 中声明依赖版本号，所有版本由父 POM BOM 统一管理。
- 不要在 `common` 模块引入 Spring Boot starter 或 Dubbo 等重型依赖，保持其为纯接口/DTO/工具模块。
- 不要跨服务直接访问对方的数据库表，每服务只操作自己的 schema。
- 不要跳过 `signaling-service` 直接调用 `ws-gateway-service` 的推送接口。
- 不要在 `gateway-service` 中编写业务逻辑，它只负责路由、鉴权、限流。
- 不要在骨架阶段引入 Phase 2 的 AI 相关依赖（Spring AI / SAA / Milvus 等）。
- 不要使用 Flyway 或其他数据库迁移工具做自动 DDL，本项目手动管理 SQL。
- 不要新增循环依赖；服务间调用方向必须单向：`gateway → 业务服务`、`message → Kafka → signaling → ws-gateway`。
- 不要只改实现不更新 `API/api-v1.md`（接口契约同步义务，见"接口契约"章节）。

## 前端开发约束

### 技术栈

React 19 + TypeScript（strict，6.x）+ Vite 8 + antd 6（组件库）+ TanStack Query 5（服务端状态）+ Zustand 5（客户端状态）+ axios（HTTP）+ json-bigint（大整数）+ react-router 8 + Tailwind CSS 4 + vitest + Testing Library（测试）。

### 结构

```text
aim/frontend/
├── package.json              # pnpm 管理
└── src/
    ├── apis/                 # 请求层：client.ts（baseURL/鉴权/静默刷新）+ 各域 xxx.ts + queryKeys.ts（Query key 统一）
    ├── mocks/                # Mock 平台（先行开发）：handlers/ 按域定义接口行为（即契约）+ db/ + realtimeHub
    ├── types/                # DTO 类型定义（与 API/api-v1.md 字段一一对应）
    ├── realtime/             # WebSocket：protocol.ts（事件名契约）+ wsChannel/mockChannel + dispatcher（事件分发）
    ├── stores/               # Zustand store（useAuthStore/useTypingStore/useWorkspaceStore/useComposerStore 等）
    ├── modules/              # 领域模块（conversation/message/...）：组件 + hooks + 缓存逻辑
    ├── components/           # 跨领域组件：ui/（自绘基座）+ layout/
    ├── pages/                # 页面级组件（Login/Register/Home/Contacts/Notifications）
    ├── router/               # 路由树 + 守卫（GuestOnly 等）
    ├── hooks/                # 跨领域 hooks（useAuth 等）
    ├── lib/                  # 工具：ids.ts（Int64 处理）/result.ts（Result 解包）/errorCodes.ts（错误码文案）
    ├── config/               # 环境配置（env.ts：baseURL/WS 地址）
    └── workers/              # Web Worker（hash 计算等）
```

### 约定

- **Mock 先行**：开发以 `src/mocks/handlers/` 的 mock 实现为准（接口行为即契约）；mock 行为变更必须同步更新 `API/api-v1.md`。
- **API 封装**：所有请求经 `src/apis/xxx.ts` 的函数封装（内部走 `client.ts`），组件/页面禁止直接 `axios`/`fetch`。
- **统一响应处理**：`client.ts` 自动解包 `Result<T>`（`code===0` 成功取 data）；401/10005/10006 自动触发静默刷新（access 剩余 <30s 主动刷新，刷新失败清会话）；错误文案优先级：`lib/errorCodes.ts` 映射表 > 服务端 message > 默认文案。
- **大整数**：所有 id/seq 为 Java long，经 json-bigint（storeAsString）承接为十进制字符串；类型用 `Int64 = string | number`；比较/展示用 `lib/ids.ts` 的 `toInt64String`，禁止直接数字比较。
- **时间单位**：一律 epoch 毫秒；唯一例外 `muteUntil`/`muteUntilSec` 为 epoch 秒（0 = 未禁言/永久）。
- **状态管理**：服务端状态（列表/详情/分页缓存）用 TanStack Query，Query key 统一定义在 `src/apis/queryKeys.ts`；客户端状态（UI/会话/typing）用 Zustand（`src/stores/`）。
- **WebSocket**：事件名以 `src/realtime/protocol.ts` 与契约第 9 章为准——上行 `typing`/`typing_stop`，下行 `typing.notify`/`typing.stop`（上下行同名事件禁止）；改动必须同步契约文档。
- **分页**：参数 `pageNum`（默认 1）/`pageSize`（服务端钳制 ≤100，各接口默认见契约）；列表响应 `{list, total}` 或按契约。
- **组件**：优先 antd + Tailwind；`src/components/ui/` 为自绘基座组件（Avatar/Badge/Dialog 等）。
- **测试**：vitest + jsdom + Testing Library；纯逻辑（lib/mocks/cache/dispatcher）直跑单测，组件/hooks 用 `*.test.tsx`；mock handlers 有 `platform.test.ts` 守护契约行为。

### 反模式

- 不要在组件/页面里直接写 `axios`/`fetch`，一律走 `src/apis/` 封装。
- 不要绕过 `Result` 解包直接读响应（`data` 可能为 `null`）。
- 不要手写数字比较/拼接 long id（精度丢失），用 `toInt64String`。
- 不要只改 mock 不更新 `API/api-v1.md`（契约同源义务）。
- 不要随意改 `realtime/protocol.ts` 事件名不同步契约（上下行事件禁止同名）。
- 不要在前端类型里定义与契约字段名不一致的 DTO（如加 `is-` 前缀的布尔键）。
- 不要跳过 `pnpm typecheck`/`pnpm test` 提交代码。

## 命令

后端（`backend/` 目录）：

```bash
mvn validate                     # 验证 POM 结构与依赖解析
mvn compile                      # 全量编译
mvn compile -pl common           # 单模块编译
mvn test                         # 运行测试
docker compose up -d             # 启动基础设施（PG/Redis/Kafka/Nacos/MinIO）
```

前端（`frontend/` 目录）：

```bash
pnpm dev                         # 启动开发服务器（默认 mock 模式）
pnpm typecheck                   # 类型检查（tsc -b --noEmit）
pnpm test                        # 单元测试（vitest）
pnpm lint                        # ESLint
pnpm build                       # 生产构建
```
