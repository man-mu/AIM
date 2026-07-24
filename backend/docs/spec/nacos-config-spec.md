# Nacos Config 集成规范

为微服务接入 Nacos 配置中心的统一指南。user-service / file-service 为参考实现，其他服务按本规范接入。

## 1. 整体思路

| 层 | 职责 | 文件 |
|----|------|------|
| **application.yml** | 既写 Nacos 连接属性，又用 `spring.config.import` 触发拉取，再保留最小兜底 | `src/main/resources/application.yml` |
| **Nacos DataId** | 真正的业务配置（数据源、Redis、Dubbo、JWT 等） | Nacos 配置中心对应 namespace |
| **nacos-init-data.sql** | 初始化脚本（首次手动执行） | `docs/sql/init/nacos-init-data.sql` |

**单一配置源**：本地只管「怎么连 Nacos + 触发拉取」，业务参数一律放 Nacos DataId，环境隔离通过 `NACOS_NAMESPACE=dev|test|prod` 切换 namespace 实现。

**为什么不用 bootstrap.yml**：Spring Cloud 2025.0.0 + Spring Cloud Alibaba 2023.0.x 下 `bootstrap.yml` 不再自动触发 Nacos config 拉取（即使引入 `spring-cloud-starter-bootstrap` 也不可靠）。改用 `spring.config.import` 方式，由 Spring Boot 原生 ConfigData API 在启动早期主动拉取，更稳定。

## 2. 代码层改动（每个新服务 3 件事）

### 2.1 pom.xml 加 2 个依赖（版本由父 POM 管理，不声明 version）

```xml
<!-- Nacos 服务发现（如果已有 discovery 可跳过） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<!-- Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

**注意**：不再需要 `spring-cloud-starter-bootstrap`。本项目已全面切换到 `spring.config.import` 方式。

### 2.2 application.yml 完整模板

一份 application.yml 同时承担三件事：① Nacos 连接属性 ② 触发拉取 ③ 兜底。**不再新建 bootstrap.yml**。

**模板**（替换 `{service-name}` / `{HTTP 端口，纯 Dubbo Provider 则删掉 server.port}`）：

```yaml
# application.yml
# 1) Nacos 连接属性 + 触发拉取  2) 最小兜底
# 业务配置全部在 Nacos 配置中心对应 namespace 的 {service-name}.yml DataId 下
spring:
  application:
    name: {service-name}              # 必须与 DataId 文件名一致
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
      - optional:nacos:{service-name}.yml
      - optional:nacos:application.yml?group=COMMON_GROUP

# 纯 Dubbo Provider 没有 HTTP 端口，删掉 server 段
server:
  port: ${SERVER_PORT:xxxx}
```

**关键点**：
- `spring.application.name` 必须与 Nacos 里的 DataId 文件名一致（`{service-name}.yml`）
- `server-addr / namespace / username / password` 放 `spring.cloud.nacos` **公共层**，config 和 discovery 自动继承
- `group` **不放公共层**——discovery 默认 `DEFAULT_GROUP`，config 用 `AIM_GROUP`，语义不同
- `spring.config.import` 是触发 Nacos 拉取的**唯一开关**：第一条拉本服务 DataId，第二条拉共享公共配置
- `optional:` 前缀让 Nacos 不可达时不报错，走本地兜底；生产环境想强校验可去掉 `optional:`

### 2.3 不要新建 bootstrap.yml

**禁止**再创建 `bootstrap.yml` 文件——本项目不再使用 bootstrap 机制。如果旧服务还残留 `bootstrap.yml`，接入本规范时删除它，并把里面的 Nacos 连接属性合并到 `application.yml` 的 `spring.cloud.nacos` 下。

### 2.4 标注热刷新 Bean（可选）

如果某个 Bean 的字段通过 `@Value` 注入了 Nacos 管理的配置，且希望在 Nacos 控制台改配置后**不重启就生效**，加 `@RefreshScope`：

```java
@Service
@Slf4j
@RefreshScope                        // 配置变更后 Bean 重建
public class XxxServiceImpl implements XxxService {
    private final byte[] jwtSecretBytes;     // @Value 注入，Nacos 改了能热刷新
    // ...
}
```

**注意**：`@RefreshScope` 会给 Bean 加 CGLIB 代理，构造器注入不受影响。不是所有 Bean 都需要加，只有持有「可能热变更的 @Value」字段的 Bean 才标。Mapper、Mapper 依赖的 DataSource 之类不要标。

## 3. Nacos DataId 内容规范

DataId = `{service-name}.yml`，内容是完整的业务 YAML。**关键约定**：

### 3.1 敏感值用占位符，不在 Nacos 存明文

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/aim?currentSchema=%22{schema}%22&stringtype=unspecified
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
```

- 占位符由 Spring Boot 在加载 YAML 后解析
- 解析顺序：命令行参数 > OS 环境变量 > JVM 系统属性 > 默认值（`{...:localhost}` 里的 `localhost`）
- dev namespace 的 DataId bake 了 `localhost` 默认值，本地开发零配置
- test/prod namespace 同样用占位符但由运维通过环境变量注入具体值

### 3.2 各服务 DataId 应包含的字段

| 字段类别 | 字段 | Nacos 还是本地兜底 |
|---------|------|-------------------|
| 数据源 | `spring.datasource.url/username/password/driver` | Nacos |
| Redis | `spring.data.redis.host/port` | Nacos |
| Dubbo | `dubbo.application.name/protocol.port/registry.address` | Nacos（registry 地址必须带鉴权，见 3.4） |
| MyBatis | `mybatis-plus.configuration.map-underscore-to-camel-case` | Nacos（共享 common DataId 已有） |
| 雪花算法 | `aim.snowflake.worker-id` | Nacos（每个服务分配不同 worker-id） |
| 业务专属 | JWT 密钥 / 文件大小阈值等 | Nacos |
| 服务端口 | `server.port` | 本地兜底（见 application.yml；纯 Dubbo Provider 无此项） |
| Nacos 连接 | `spring.cloud.nacos.*` | 本地 application.yml（必须，触发拉取用） |

### 3.3 共享配置 COMMON_GROUP/application.yml

放所有服务通用的配置，每个服务通过 `spring.config.import` 第二条拉取。当前内容：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

新增通用配置时**优先往这里加**，而不是每个服务的 DataId 各写一份（DRY）。

### 3.4 Dubbo registry 地址必须带鉴权（重要）

Dubbo 3.3.x 在向 Nacos 注册/发现服务时，会**独立调用 Nacos OpenAPI**，不走 `spring.cloud.nacos` 的公共属性继承。如果 `dubbo.registry.address` 只写 `nacos://localhost:8848`，Dubbo 会以匿名身份访问，被 Nacos 鉴权拒绝（HTTP 401），启动卡死。

**正确格式**（username/password/namespace/group 全部嵌进 URL）：

```yaml
dubbo:
  registry:
    address: nacos://${NACOS_USERNAME:nacos}:${NACOS_PASSWORD:nacos}@${NACOS_ADDR:localhost:8848}?namespace=${NACOS_NAMESPACE:dev}&group=${NACOS_GROUP:AIM_GROUP}
```

- `nacos://用户名:密码@地址?namespace=xxx&group=yyy` 是 Dubbo Nacos 注册中心的标准鉴权 URL 格式
- 复用 `${NACOS_USERNAME}` 等占位符，与 application.yml 里的 Nacos 连接属性保持单一来源
- 不要写成 `nacos://localhost:8848`（无鉴权）或 `nacos://nacos:nacos@localhost:8848`（缺 namespace/group，会注册到默认 namespace 的 DEFAULT_GROUP）

## 4. 初始化脚本集成（nacos-init-data.sql）

新增服务的 DataId 要追加到 `docs/sql/init/nacos-init-data.sql` 的 `DO $$ ... $$` 块里。

### 步骤

1. 在 `DECLARE` 区追加一个 `TEXT` 变量，内容是该服务的完整 YAML（用 `E'...\n...'` 转义换行）。**dubbo.registry.address 必须按 3.4 的鉴权格式写**。

2. 在 `FOREACH ns IN ARRAY ns_list LOOP` 里追加一条 INSERT：

```sql
INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
VALUES ('{service-name}.yml', 'AIM_GROUP', {content_var}, md5({content_var}), '127.0.0.1', ns, 'yaml')
ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;
```

3. 脚本幂等：`ON CONFLICT DO NOTHING`，已有 DataId 不会被覆盖。**更新 DataId 内容时需要先删除旧记录再重新执行脚本**：

```sql
-- 删旧记录（按 namespace + group + dataId 精确定位）
DELETE FROM config_info
WHERE group_id='AIM_GROUP' AND data_id='{service-name}.yml' AND tenant_id IN ('dev','test','prod');
```

删完再 `psql -f docs/sql/init/nacos-init-data.sql` + `docker compose restart nacos`。

### 验证 DataId 真实存储内容

```bash
TOKEN=$(curl -s -X POST 'http://localhost:8848/nacos/v1/auth/users/login' \
    -d 'username=nacos&password=nacos' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
curl -s "http://localhost:8848/nacos/v3/admin/cs/config?accessToken=${TOKEN}&namespaceId=dev&groupName=AIM_GROUP&dataId={service-name}.yml"
```

## 5. 配置优先级总览

加载完成后 Spring 的 PropertySource 优先级（高 → 低）：

```
1. 命令行参数 (-Dxxx=yyy)
2. OS 环境变量 (export DB_HOST=...)
3. JVM 系统属性
4. Nacos DataId（{service-name}.yml，按 namespace 隔离）
5. Nacos shared-configs（COMMON_GROUP/application.yml）
6. 本地 application.yml（含 Nacos 连接属性 + 兜底）
7. 占位符默认值（${DB_HOST:localhost} 里的 localhost）
```

**含义**：
- 命令行 / 环境变量 > Nacos > 本地兜底，运维通过环境变量可覆盖任何 Nacos 配置
- Nacos 里 DataId 同名字段会覆盖本地 application.yml，所以 local 放了什么 Nacos 都能覆写
- 占位符有默认值时即使没设任何环境变量也能在 dev 环境跑起来
- **本地 application.yml 里的 `spring.cloud.nacos.*` 是触发拉取的根基，不能被 Nacos DataId 覆盖**（否则会循环依赖）——所以 Nacos DataId 里不要写 `spring.cloud.nacos` 段

## 6. 踩坑录

### 6.1 Nacos v3.2.2 API 完全移除 v1 HTTP 端点

- `POST /nacos/v1/cs/configs`、`POST /nacos/v1/console/namespaces` 等全部 404
- 客户端 SDK 走 gRPC 不受影响，HTTP 仅用于手动管理
- 自动化初始化**直接插 PG**，不要 REST API——既绕过鉴权又省去 token 管理

### 6.2 Nacos v3 PG 模式不自动创建 admin 用户

- 设 `NACOS_AUTH_ADMIN_INITIAL_USER` 环境变量也不生效
- 必须通过 SQL 手动插入 `users` + `roles`（带 BCrypt hash）
- 此步骤已写入 `nacos-init-data.sql` 的「0. 管理员用户」段

### 6.3 namespace 无 REST API

- Nacos v3 把 namespace 的管理 API 摘干净了
- namespace 通过直插 `tenant_info` 表创建

### 6.4 SQL 执行完必须 restart Nacos

- 直接写 `config_info` 表不会被 Nacos 内存缓存感知
- 控制台看不到新配置也会查不到
- `docker compose restart nacos` 后 Nacos 从 PG 重新加载，一切生效

### 6.5 不要写 sh 脚本做 Nacos 远程初始化

试过的弯路：写 `init-nacos-config.sh` 用 curl 调 REST API，结果到处是 v3 API 兼容性陷阱、token 失效、namespace endpoint 404。

**正确做法**：一条 SQL 把 namespace / users / DataId 全 INSERT 进 PG，再 restart Nacos 一次。简单可靠。

### 6.6 bootstrap.yml 在 Spring Cloud 2025.0.0 下不触发 Nacos config 拉取

- 即使引入 `spring-cloud-starter-bootstrap`，Spring Cloud Alibaba 2023.0.x 的 Nacos config 也不在 bootstrap 阶段拉取
- 启动日志看不到 `Located property source`，DataSource 因拿不到 url 直接报错
- **正确做法**：删 `bootstrap.yml` + 删 `spring-cloud-starter-bootstrap` 依赖，改用 application.yml 里的 `spring.config.import` 触发拉取（见第 2 节）

### 6.7 Dubbo registry 地址缺鉴权 → 启动 401 卡死

- `dubbo.registry.address: nacos://localhost:8848` 会让 Dubbo 匿名访问 Nacos OpenAPI，被鉴权拒绝
- `spring.cloud.nacos.username/password` **不会被 Dubbo 自动继承**，必须显式写进 registry URL
- **正确格式**：`nacos://user:pass@host:port?namespace=xxx&group=yyy`（见 3.4）

### 6.8 application.yml 不要分 profile 文件

试过的弯路：写 `application-dev.yml` / `application-test.yml` / `application-prod.yml` 三份，最后发现：
- Nacos 里已经有 namespace 隔离了，本地再分 profile 是重复维度
- dev 有默认值、prod 强制注入的风格也混乱
- 三个 profile 文件 80% 内容相同，改数据源要改三处

**正确做法**：一个 `application.yml` 放最小兜底 + Nacos 连接属性，环境差异全部交给 Nacos namespace。

### 6.9 spring.cloud.nacos 公共属性 vs 各模块单独声明

- `server-addr / namespace / username / password` **提到 `spring.cloud.nacos` 顶层**
- `group` **不提**（config 用 `AIM_GROUP`，discovery 默认 `DEFAULT_GROUP`，语义不同）
- 写两遍就是 DRY 违反

## 7. 常用运维命令

```bash
# 看 Nacos 健康状态
curl -s http://localhost:8848/nacos/actuator/health

# 登录拿 token
TOKEN=$(curl -s -X POST 'http://localhost:8848/nacos/v1/auth/users/login' \
    -d 'username=nacos&password=nacos' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# 列出某 namespace 的所有配置
curl -s "http://localhost:8848/nacos/v3/admin/cs/config/list?accessToken=${TOKEN}&namespaceId=dev&groupName=AIM_GROUP&pageNo=1&pageSize=20"

# 看具体 DataId 内容
curl -s "http://localhost:8848/nacos/v3/admin/cs/config?accessToken=${TOKEN}&namespaceId=dev&groupName=AIM_GROUP&dataId=user-service.yml"

# PG 直查配置表（不依赖 token，但只看明文总数和清单）
docker exec aim-postgres psql -U postgres -d nacos -c \
    "SELECT tenant_id, group_id, data_id FROM config_info ORDER BY tenant_id, group_id;"

# 切换环境（本地调试时）
NACOS_NAMESPACE=test mvn spring-boot:run -pl {service-name}

# 在 IDE 的 Run Configuration 里设 env vars
#   NACOS_NAMESPACE=test
#   NACOS_ADDR=localhost:8848

# 删除某服务的旧 DataId（更新内容前用）
docker exec aim-postgres psql -U postgres -d nacos -c \
    "DELETE FROM config_info WHERE group_id='AIM_GROUP' AND data_id='{service-name}.yml' AND tenant_id IN ('dev','test','prod');"
docker compose restart nacos

# 重置整个 Nacos 配置（危险！会清空所有 DataId）
docker exec aim-postgres psql -U postgres -d nacos -c "DELETE FROM config_info WHERE group_id='AIM_GROUP';"
docker compose restart nacos
```

## 8. 检查清单（新服务接入时对照）

- [ ] pom.xml 加 `nacos-discovery` + `nacos-config` 依赖（**不要**加 `spring-cloud-starter-bootstrap`）
- [ ] **不要**新建 `bootstrap.yml`
- [ ] application.yml 含 `spring.cloud.nacos` 公共连接属性 + `spring.config.import` 触发拉取 + 最小兜底
- [ ] `@RefreshScope` 加在持有 `@Value` 注入「可能热变更」字段的 Service Bean 上
- [ ] `nacos-init-data.sql` 的 DO 块里追加该服务的 DataId INSERT，`dubbo.registry.address` 按鉴权格式写
- [ ] `docs/sql/init/nacos-init-data.sql` 执行一次 + `docker compose restart nacos`
- [ ] 三个 namespace（dev/test/prod）都能在控制台看到该 DataId
- [ ] 本地 `mvn spring-boot:run` 启动日志里出现 Nacos config 拉取成功、Dubbo 注册成功（无 401）
- [ ] 控制台改一个非关键字段（如 `aim.snowflake.worker-id`），验证不重启生效
