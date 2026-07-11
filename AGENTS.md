 0# AGENTS.md

## 语言

- 主/从 agent 必须默认使用 `简体中文` 作为自然语言。
- 写文档、总结、计划、问题澄清时优先中文；代码标识符和命令保持原文。


## 工作流

- 代码更改后必须执行 `mvn compile` 验证编译通过；涉及测试的改动须 `mvn test` 通过
- 任务完成后总结工作区改动并 `git commit`，message 必须符合 [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) 规范（如 `feat:` / `fix:` / `docs:` / `refactor:`）
- 任务完成后必须清理临时文件，不要将它们留在工作目录或提交到版本控制

## 结构

```text
aim/
├── pom.xml                       # 父 POM（聚合 + BOM 依赖版本管理）
├── common/                       # 共享模块（Dubbo 接口、常量、事件 DTO、异常、工具）
├── gateway-service/              # REST API 网关（Spring Cloud Gateway）
├── ws-gateway-service/           # WebSocket 网关（Netty + 内部 Dubbo 推送）
├── user-service/                 # 用户服务（Dubbo Provider）
├── friend-service/               # 好友服务（Dubbo Provider）
├── conv-service/                 # 会话群组服务（Dubbo Provider）
├── message-service/              # 消息引擎（Dubbo + Kafka + Outbox）
├── signaling-service/            # 信令推送（Kafka Consumer + Fanout）
├── file-service/                 # 文件管理（MinIO）
├── bot-service/                  # Phase 2: Bot 管理 + AI 执行
├── kb-service/                   # Phase 2: 知识库 RAG
├── llm-gateway-service/          # Phase 2: LLM 模型网关
├── docker-compose.yml            # 本地基础设施（PG/Redis/Kafka/Nacos/MinIO）
└── plan.md                       # 骨架初始化计划
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

## 约定

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

## 反模式

- 不要在子模块 `pom.xml` 中声明依赖版本号，所有版本由父 POM BOM 统一管理。
- 不要在 `common` 模块引入 Spring Boot starter 或 Dubbo 等重型依赖，保持其为纯接口/DTO/工具模块。
- 不要跨服务直接访问对方的数据库表，每服务只操作自己的 schema。
- 不要跳过 `signaling-service` 直接调用 `ws-gateway-service` 的推送接口。
- 不要在 `gateway-service` 中编写业务逻辑，它只负责路由、鉴权、限流。
- 不要在骨架阶段引入 Phase 2 的 AI 相关依赖（Spring AI / SAA / Milvus 等）。
- 不要使用 Flyway 或其他数据库迁移工具做自动 DDL，本项目手动管理 SQL。
- 不要新增循环依赖；服务间调用方向必须单向：`gateway → 业务服务`、`message → Kafka → signaling → ws-gateway`。

## 命令

```bash
mvn validate                     # 验证 POM 结构与依赖解析
mvn compile                      # 全量编译
mvn compile -pl common           # 单模块编译
mvn test                         # 运行测试
docker compose up -d             # 启动基础设施（PG/Redis/Kafka/Nacos/MinIO）
```

## 本机环境

- JDK 21：通过 SDKMAN 管理（`~/.sdkman/candidates/java/current`）
- Maven 3.9.16：通过 SDKMAN 管理（`~/.sdkman/candidates/maven/current`，已配阿里云镜像）
- 网络问题：下载依赖失败时设置代理 `export http_proxy=http://127.0.0.1:7890; export https_proxy=http://127.0.0.1:7890`
- 参考项目：`/Users/manmu/code/GoLang_projrct/MAIM`（Go 实现的同类 IM 系统）
- 需求文档：`/Users/manmu/code/IDEA_project/AIM/要求.md`
- 骨架计划：`/Users/manmu/code/IDEA_project/AIM/plan.md`
