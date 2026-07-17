# Nacos Config 集成规范

为微服务接入 Nacos 配置中心的统一指南。user-service 为参考实现，其他服务按本规范接入。

## 1. 整体思路

| 层 | 职责 | 文件 |
|----|------|------|
| **bootstrap.yml** | 告诉 Spring「去哪个 Nacos、哪个 namespace 拉 DataId」 | `src/main/resources/bootstrap.yml` |
| **application.yml** | 兜底配置，Nacos 不可达时让应用依然能启动 | `src/main/resources/application.yml` |
| **Nacos DataId** | 真正的业务配置（数据源、Redis、Dubbo、JWT 等） | Nacos 配置中心对应 namespace |
| **nacos-init-data.sql** | 初始化脚本（首次手动执行） | `docs/sql/init/nacos-init-data.sql` |

**单一配置源**：本地只管「怎么连 Nacos」，业务参数一律放 Nacos DataId，环境隔离通过 `NACOS_NAMESPACE=dev|test|prod` 切换 namespace 实现。

## 2. 代码层改动（每个新服务 3 件事）

### 2.1 pom.xml 加 3 个依赖（版本由父 POM 管理，不声明 version）

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
<!-- bootstrap.yml 加载支持（Spring Cloud 2020+ 必须显式引入） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

### 2.2 新建 bootstrap.yml

**模板**（替换 `{service-name}` / `{Dubbo 端口}` / `{业务配置内容}`）：

```yaml
spring:
  application:
    name: {service-name}              # 必须与 DataId 文件名一致（Nacos 默认按 application.name 找 DataId）
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
        shared-configs:
          - data-id: application.yml
            group: COMMON_GROUP
            refresh: true
      discovery:
        group: ${NACOS_GROUP:AIM_GROUP}
```

**关键点**：
- `spring.application.name` 必须与 Nacos 里的 DataId 文件名一致（`{service-name}.yml`）
- `server-addr / namespace / username / password` 放 `spring.cloud.nacos` **公共层**，config 和 discovery 自动继承
- `group` **不放公共层**——discovery 默认 `DEFAULT_GROUP`，config 用 `AIM_GROUP`，语义不同
- `shared-configs` 声明 `COMMON_GROUP/application.yml` 让所有服务共享公共配置

### 2.3 精简 application.yml

只留「Nacos 挂了也要能起来」的最小兜底：

```yaml
# 兜底配置：仅在 Nacos 不可达时让应用能独立启动
# 所有业务配置在 Nacos 配置中心对应 namespace 的 {service-name}.yml DataId 下
server:
  port: ${SERVER_PORT:xxxx}           # 服务 HTTP 端口
```

**不要**在 application.yml 里放数据源、Redis、Dubbo、JWT 等业务配置——它们统统进 Nacos。

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
| Dubbo | `dubbo.application.name/protocol.port/registry.address` | Nacos |
| MyBatis | `mybatis-plus.configuration.map-underscore-to-camel-case` | Nacos（共享 common DataId 已有） |
| 雪花算法 | `aim.snowflake.worker-id` | Nacos（每个服务分配不同 worker-id） |
| 业务专属 | JWT 密钥 / 文件大小阈值等 | Nacos |
| 服务端口 | `server.port` | 本地兜底（见 application.yml） |

### 3.3 共享配置 COMMON_GROUP/application.yml

放所有服务通用的配置，每个服务通过 `shared-configs` 拉取。当前内容：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

新增通用配置时**优先往这里加**，而不是每个服务的 DataId 各写一份（DRY）。

## 4. 初始化脚本集成（nacos-init-data.sql）

新增服务的 DataId 要追加到 `docs/sql/init/nacos-init-data.sql` 的 `DO $$ ... $$` 块里。

### 步骤

1. 在 `DECLARE` 区追加一个 `TEXT` 变量，内容是该服务的完整 YAML（用 `E'...\n...'` 转义换行）

2. 在 `FOREACH ns IN ARRAY ns_list LOOP` 里追加一条 INSERT：

```sql
INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
VALUES ('{service-name}.yml', 'AIM_GROUP', {content_var}, md5({content_var}), '127.0.0.1', ns, 'yaml')
ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;
```

3. 脚本幂等：`ON CONFLICT DO NOTHING`，已有 DataId 不会被覆盖。更新 DataId 内容时需要先在 Nacos 控制台或 PG 删除旧记录再重新执行脚本，或直接通过控制台编辑。

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
6. 本地 application.yml 兜底
7. 占位符默认值（${DB_HOST:localhost} 里的 localhost）
```

**含义**：
- 命令行 / 环境变量 > Nacos > 本地兜底，运维通过环境变量可覆盖任何 Nacos 配置
- Nacos 里 DataId 同名字段会覆盖本地 application.yml，所以 local 放了什么 Nacos 都能覆写
- 占位符有默认值时即使没设任何环境变量也能在 dev 环境跑起来

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

### 6.6 bootstrap.yml 公共属性 vs 各模块单独声明

- `server-addr / namespace / username / password` **提到 `spring.cloud.nacos` 顶层**
- `group` **不提**（config 用 `AIM_GROUP`，discovery 默认 `DEFAULT_GROUP`，语义不同）
- 写两遍就是 DRY 违反

### 6.7 application.yml 不要分 profile 文件

试过的弯路：写 `application-dev.yml` / `application-test.yml` / `application-prod.yml` 三份，最后发现：
- Nacos 里已经有 namespace 隔离了，本地再分 profile 是重复维度
- dev 有默认值、prod 强制注入的风格也混乱
- 三个 profile 文件 80% 内容相同，改数据源要改三处

**正确做法**：一个 `application.yml` 放最小兜底，环境差异全部交给 Nacos namespace。

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

# 重置整个 Nacos 配置（危险！会清空所有 DataId）
docker exec aim-postgres psql -U postgres -d nacos -c "DELETE FROM config_info WHERE group_id='AIM_GROUP';"
docker compose restart nacos
```

## 8. 检查清单（新服务接入时对照）

- [ ] pom.xml 加 `nacos-config` + `spring-cloud-starter-bootstrap` 依赖
- [ ] 新建 bootstrap.yml（参考模板，改 `spring.application.name` 和 group）
- [ ] 精简 application.yml（只留 server.port）
- [ ] `@RefreshScope` 加在持有 `@Value` 注入「可能热变更」字段的 Service Bean 上
- [ ] `nacos-init-data.sql` 的 DO 块里追加该服务的 DataId INSERT
- [ ] `docs/sql/init/nacos-init-data.sql` 执行一次 + `docker compose restart nacos`
- [ ] 三个 namespace（dev/test/prod）都能在控制台看到该 DataId
- [ ] 本地 `mvn spring-boot:run` 启动日志里出现 `Located property source: {service-name}.yml`
- [ ] 控制台改一个非关键字段（如 `aim.snowflake.worker-id`），验证不重启生效