# AIM 项目工程骨架初始化计划

> **本计划唯一目标**：初始化 Java 微服务工程的骨架结构，包括父 POM、共享模块、各微服务启动骨架、基础设施编排。
> **严禁**实现任何实际业务代码（业务逻辑、数据库 DDL、Dubbo 接口实现、Kafka 消费逻辑等均在本计划范围之外）。
> 参考 Go 项目：`/Users/manmu/code/GoLang_projrct/MAIM`
> 需求文档：`/Users/manmu/code/IDEA_project/AIM/要求.md`

---

## 1. 技术栈与版本锁定

### 1.1 核心框架

| 组件 | 版本 | 备注 |
|------|------|------|
| JDK | 21 (LTS) | 启用虚拟线程 |
| Maven | 3.9.16 | |
| Spring Boot | 3.5.16 | `spring-boot-starter-parent` |
| Spring Cloud | 2025.0.x | BOM |
| Spring Cloud Alibaba | 2025.0.0.0 | BOM（Nacos + Sentinel） |
| Apache Dubbo | 3.3.6 | 服务间 RPC |
| MyBatis-Plus | 3.5.17 | `mybatis-plus-spring-boot3-starter` |

### 1.2 Phase 2 AI 专用（仅预留版本，不在此阶段引入）

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring AI | 1.1.2 | SAA 依赖管理 |
| Spring AI Alibaba | 1.1.2.2 | Agent Framework |
| spring-ai-alibaba-agent-framework | 1.1.2.2 | ReactAgent |
| spring-ai-alibaba-starter-dashscope | 1.1.2.1 | 通义千问模型 |
| spring-ai-milvus-store | 随 Spring AI 1.1.2 | 向量检索 |
| spring-ai-mcp | 随 Spring AI 1.1.2 | MCP 工具集成 |

> ⚠️ Spring AI Alibaba 2.0 需要 Spring Boot 4.0，与本项目 Boot 3.5.16 不兼容，故使用 1.1.2.2 稳定版。

### 1.3 中间件服务端（docker-compose 编排）

| 组件 | 版本 | 说明 |
|------|------|------|
| PostgreSQL | 16 | UPSERT + SKIP LOCKED |
| Redis | 7.4 | 未读计数 + 幂等 + 缓存 |
| Kafka | 3.9 (KRaft 模式) | 事件总线，无需 Zookeeper |
| Nacos Server | 3.2.2 | 注册中心 + 配置中心（Server 需 JDK 17+） |
| MinIO | latest | 对象存储 |

### 1.4 工具库

| 组件 | 版本 | 用途 |
|------|------|------|
| MinIO Client | 8.5.14 | 文件上传 |
| Hutool | 5.8.32 | 通用工具集 |
| FastJSON2 | 2.0.60 | JSON 序列化 |
| MapStruct | 1.6.3 | DTO ↔ Entity 转换 |
| Sentinel | 1.8.8（SCA 内置） | 限流熔断 |
| Lombok | 随 Boot | 简化 POJO |
| Flyway | **不使用** | 手动 SQL 管理 |

---

## 2. 项目结构（Monorepo 多模块）

### 2.1 目录树

```
/Users/manmu/code/IDEA_project/AIM/
├── pom.xml                              # 父 POM (聚合 + BOM 依赖版本管理)
├── common/                              # 共享模块
│   ├── pom.xml
│   └── src/main/java/lanshan/manmu/common/
│       ├── constant/                    # 常量类骨架
│       ├── event/                        # Kafka 事件 DTO 骨架
│       ├── result/                       # 统一响应骨架
│       ├── exception/                    # 异常骨架
│       └── util/                         # 工具类骨架
├── gateway-service/                     # REST API 网关 (Spring Cloud Gateway)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/gateway/
│       └── resources/application.yml
├── ws-gateway-service/                  # WebSocket 网关 (Netty + 内部 Dubbo 推送)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/wsgateway/
│       └── resources/application.yml
├── user-service/                        # 用户服务 (Dubbo Provider)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/user/
│       └── resources/application.yml
├── friend-service/                      # 好友服务 (Dubbo Provider)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/friend/
│       └── resources/application.yml
├── conv-service/                        # 会话服务 (Dubbo Provider)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/conv/
│       └── resources/application.yml
├── message-service/                     # 消息引擎 (Dubbo + Kafka + Outbox)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/message/
│       └── resources/application.yml
├── signaling-service/                   # 信令推送 (Kafka Consumer + Fanout)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/signaling/
│       └── resources/application.yml
├── file-service/                        # 文件管理 (MinIO)
│   ├── pom.xml
│   └── src/main/
│       ├── java/lanshan/manmu/file/
│       └── resources/application.yml
├── bot-service/                         # Phase 2: Bot 管理 + AI 执行 (骨架预留)
├── kb-service/                          # Phase 2: 知识库 RAG (骨架预留)
├── llm-gateway-service/                 # Phase 2: LLM 模型网关 (骨架预留)
├── docker-compose.yml                   # PG / Redis / Kafka / Nacos / MinIO
└── .env.example
```

### 2.2 每个微服务模块内部结构

````
```
xxx-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/lanshan/manmu/<module>/
    │   │   ├── XxxServiceApplication.java   # 启动类
    │   │   ├── config/                        # 配置类
    │   │   ├── rpc/                           # Dubbo 服务实现 (@DubboService)
    │   │   ├── service/                       # 业务逻辑层
    │   │   │   └── impl/
    │   │   ├── mapper/                        # MyBatis-Plus Mapper
    │   │   ├── model/
    │   │   │   ├── entity/                    # 数据库实体
    │   │   │   ├── dto/                        # 请求/响应 DTO
    │   │   │   └── event/                     # Kafka 事件对象
    │   │   ├── consumer/                      # Kafka 消费者
    │   │   └── dispatcher/                    # Outbox 轮询器 (message-service 专属)
    │   └── resources/
    │       ├── application.yml                # Spring Boot 配置
    │       └── mapper/                        # MyBatis XML (如需)
    └── test/java/
```
````

> 骨架阶段创建启动类和配置文件，创建 `config/`、`rpc/`、`service/`、`mapper/`、`consumer/` 等业务子包，形成项目规范，但是不要写入代码

---

## 3. 服务职责说明

### Phase 1 — 核心 IM (9 个模块)

| 服务 | 职责 | 关键技术 |
|------|------|---------|
| `common` | 共享模块：常量/事件 DTO/异常/工具 | 纯 Java |
| `gateway-service` | REST API 网关，JWT 鉴权、限流、转发 Dubbo | Spring Cloud Gateway |
| `ws-gateway-service` | WebSocket 长连接网关、在线状态、内部推送接口 | Netty + Dubbo |
| `user-service` | 注册/登录/资料管理 | Dubbo + MyBatis-Plus + PostgreSQL |
| `friend-service` | 好友增删/分组/备注/黑名单 | Dubbo + MyBatis-Plus |
| `conv-service` | 会话群组管理（成员/禁言/Bot 绑定） | Dubbo + MyBatis-Plus |
| `message-service` | 消息引擎核心 — 收发、Transactional Outbox、Inbox 写扩散 | Dubbo + Kafka + PostgreSQL 事务 |
| `signaling-service` | 事件扇出 — Kafka 消费 → ws-gateway 推送 / FCM/APNS / Bot 路由 | Kafka Consumer + Dubbo |
| `file-service` | 文件管理（MinIO Presigned URL） | MinIO Client |

### Phase 2 — AI 能力 (3 个服务)

| 服务 | 职责 | 关键技术 |
|------|------|---------|
| `bot-service` | Bot 管理 + AI 执行引擎（ReAct Agent、MCP 工具、记忆） | Spring AI Alibaba Agent Framework |
| `kb-service` | RAG 向量检索 + 知识库管理 | Spring AI VectorStore + Milvus |
| `llm-gateway-service` | LLM 多 Provider 路由 + 计费限流 | Spring AI ChatModel 抽象 |

---

## 4. 执行步骤

> 每步标注验证标准，逐步验证后再进入下一步。

### 步骤 1: 创建父 POM + BOM 依赖管理

**操作**：在 `/Users/manmu/code/IDEA_project/AIM/pom.xml` 创建父 POM。

**关键内容**：
- `groupId`: `lanshan.manmu`
- `artifactId`: `aim`
- `packaging`: `pom`
- `modules`: `common, gateway-service, ws-gateway-service, user-service, friend-service, conv-service, message-service, signaling-service, file-service`（Phase 2 的 3 个服务暂不加入 modules，留待后续阶段）
- `properties` 锁定所有版本
- `dependencyManagement` 导入 Spring Boot / Spring Cloud / Spring Cloud Alibaba BOM + 声明 Dubbo / MyBatis-Plus / MinIO / Hutool / FastJSON2 / MapStruct 版本
- `build.plugins` 配置 `maven-compiler-plugin`（JDK 21）+ `lombok`

**父 POM 关键属性**：
```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>3.5.16</spring-boot.version>
    <spring-cloud.version>2025.0.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2025.0.0.0</spring-cloud-alibaba.version>
    <dubbo.version>3.3.6</dubbo.version>
    <mybatis-plus.version>3.5.17</mybatis-plus.version>
    <postgresql.version>42.7.8</postgresql.version>
    <minio.version>8.5.14</minio.version>
    <hutool.version>5.8.32</hutool.version>
    <fastjson2.version>2.0.60</fastjson2.version>
    <mapstruct.version>1.6.3</mapstruct.version>
</properties>
```

**验证**：在 AIM 目录下执行 `mvn validate` 成功。

### 步骤 2: 创建 common 共享模块骨架

**操作**：创建 `common/pom.xml`（artifactId: `aim-common`，依赖 `lombok` + `fastjson2` + `hutool`）。

**包结构** `lanshan.manmu.common`（仅创建包目录和占位类，不实现逻辑）：
- `constant/` — 常量类占位
- `event/` — Kafka 事件 DTO 占位
- `result/` — 统一响应占位
- `exception/` — 异常占位
- `util/` — 工具类占位

> 此步骤只确保模块可编译，具体类内容留待实现阶段填充。

**验证**：`mvn compile -pl common` 成功。

### 步骤 3: 创建 Phase 1 的 8 个服务骨架

对每个服务执行以下操作：

1. 创建 `<service>/pom.xml`（artifactId: `aim-<service>`，parent 指向 `aim`，依赖 `aim-common` + 对应 starter）
2. 创建启动类 `<Service>Application.java`（`@SpringBootApplication`，无其他注解）
3. 创建 `application.yml`（端口、Nacos 地址、Dubbo 配置、数据源、Redis、Kafka 等基础配置）

**各服务依赖与端口清单**：

| 服务 | 端口 | 核心依赖 |
|------|------|---------|
| `gateway-service` | 8080 | spring-cloud-starter-gateway, spring-cloud-starter-alibaba-nacos-discovery |
| `ws-gateway-service` | 8081 | netty-all, dubbo-spring-boot-starter, spring-cloud-starter-alibaba-nacos-discovery |
| `user-service` | 20881 (Dubbo) | dubbo-spring-boot-starter, mybatis-plus-spring-boot3-starter, postgresql, spring-cloud-starter-alibaba-nacos-discovery, spring-boot-starter-data-redis |
| `friend-service` | 20882 (Dubbo) | 同 user-service |
| `conv-service` | 20883 (Dubbo) | 同 user-service |
| `message-service` | 20884 (Dubbo) | dubbo-spring-boot-starter, mybatis-plus, postgresql, spring-kafka, spring-boot-starter-data-redis |
| `signaling-service` | 20885 (Dubbo) | dubbo-spring-boot-starter, spring-kafka, spring-boot-starter-data-redis |
| `file-service` | 20886 (Dubbo) | dubbo-spring-boot-starter, minio |

**application.yml 通用模板**（以 user-service 为例）：
```yaml
server:
  port: 8081
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:postgresql://localhost:5432/aim
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: localhost
      port: 6379
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
dubbo:
  application:
    name: user-service
  protocol:
    name: dubbo
    port: 20881
  registry:
    address: nacos://localhost:8848
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

> 各服务根据自身职责调整配置项（如 message-service 需加 Kafka 配置，gateway-service 用 Spring Cloud Gateway 配置而非 Dubbo Provider 等）。

**验证**：所有服务 `mvn compile` 成功。

### 步骤 4: 编写 docker-compose.yml

**操作**：在 `/Users/manmu/code/IDEA_project/AIM/docker-compose.yml` 编排基础设施。

**包含服务**：
- `postgres:16` — 端口 5432，创建 `aim` 数据库
- `redis:7.4` — 端口 6379
- `kafka:3.9` (KRaft 模式，无需 Zookeeper) — 端口 9092
- `nacos/nacos-server:v3.2.2` — 端口 8848/9848，单机模式
- `minio/minio` — 端口 9000/9001

**验证**：`docker compose up -d` 成功启动，各服务可访问。

---

## 5. 验收清单

骨架初始化完成后应满足：

- [ ] `mvn validate`（AIM 根目录）成功
- [ ] `mvn compile`（全量）成功
- [ ] `common` 模块可独立编译
- [ ] 8 个 Phase 1 服务均可独立编译
- [ ] 每个服务都有启动类 + `application.yml`
- [ ] `docker-compose.yml` 存在且可 `docker compose up -d` 正常启动
- [ ] 父 POM 锁定所有版本，子模块不含冗余版本号
- [ ] 不存在任何业务逻辑代码（无 Dubbo 接口实现、无 Mapper、无 Service 逻辑、无 Kafka 消费者、无数据库 DDL）
