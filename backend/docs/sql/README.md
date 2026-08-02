# SQL 配置目录说明

按「执行方式」分为两类：

```
docs/sql/
├── auto/                      自动执行（docker-compose 挂载，无需手动）
│   ├── 00-init.sh             postgres entrypoint 自动执行
│   ├── nacos-pg.properties    nacos 容器自动挂载为 application.properties
│   └── schemas/               被子目录隔离（PG entrypoint 不递归子目录）
│       ├── aim-schema.sql     本项目 6 个业务 schema（user/friend/conv/msg/notify/file）
│       └── nacos-schema.sql   Nacos 13 张元数据表
└── init/                      手动执行
    ├── init-aim.sh            非 docker 环境手动建库+建表
    └── nacos-init-data.sql    Nacos 启动后初始化 admin/namespace/DataId
```

## 为什么这样分

- **auto/** 被 docker-compose 直接挂载到 postgres 容器的 `/docker-entrypoint-initdb.d/`，PG 首次启动时自动执行 `.sh` 和顶级 `.sql` 文件，零人工干预。
- schema 文件放在 `schemas/` 子目录是因为 PG entrypoint 会递归遍历**全部 SQL**（包括子目录），如果 schema 跟 `00-init.sh` 同级，会被 PG 自动执行一次，再被 `00-init.sh` 引用一次，产生重复。子目录隔离后只由 `00-init.sh` 显式 `psql -f` 引用。
- **init/** 是需要在「特定时机」手动执行的脚本——`init-aim.sh` 给非 docker 部署用，`nacos-init-data.sql` 必须等 Nacos 健康（建好表）后才能跑。

## 首次部署（docker 环境标准流程）

在 `backend/` 目录下执行：

```bash
# 1. 准备环境变量
cp .env.example .env
#   按需修改 .env 里的 POSTGRES_PASSWORD / NACOS_AUTH_TOKEN 等

# 2. 启动全部中间件（postgres 自动建 nacos 库 + 导入 schema，PG healthy 后 nacos 才启动）
docker compose up -d

# 3. 等 Nacos 健康检查通过
#    如果 curl 不可用，也可以 docker compose ps 看 nacos 状态
until curl -sf http://localhost:8848/nacos/actuator/health >/dev/null 2>&1; do
  echo "waiting for nacos..."; sleep 2
done

# 4. 执行 Nacos 初始化（建 admin 用户 + 3 个 namespace + 6 个 DataId）
docker exec -i aim-postgres psql -U postgres -d nacos < docs/sql/init/nacos-init-data.sql

# 5. 重启 Nacos 让它重新加载 PG 中已写入的数据（admin users / config 缓存）
docker compose restart nacos
```

执行完成后：

- 5 个中间件容器 Up（postgres/redis/kafka/nacos/minio）
- postgres 内 2 个数据库：`aim`（6 个业务 schema）+ `nacos`（13 张元数据表）
- Nacos 控制台 `http://localhost:8080/`，账号 `nacos / nacos`
- Nacos 配置中心 3 个 namespace（dev/test/prod）× 7 个 DataId（`application.yml` + user/file/conv/friend/message/signaling/ws-gateway 各服务配置）

## 非 docker 环境初始化

如果 PG 是独立部署（非容器），用 `init/init-aim.sh`：

```bash
bash docs/sql/init/init-aim.sh
```

脚本自动检测：

- 宿主机有 `psql` → 直连 `localhost:5432`
- 宿主机无 `psql` → 如果 `aim-postgres` 容器存在则通过 `docker exec` 执行

幂等：重复执行无害（`CREATE DATABASE` 会检测存在跳过；`CREATE TABLE IF NOT EXISTS`）。

## 幂等性

| 文件 | 幂等方式 |
|------|---------|
| `auto/00-init.sh` | 仅在 PG 卷首次创建时执行一次（PG entrypoint 规则） |
| `auto/schemas/*.sql` | 全部用 `IF NOT EXISTS`，重复 import 也跳过 |
| `init/init-aim.sh` | `CREATE DATABASE IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` |
| `init/nacos-init-data.sql` | `WHERE NOT EXISTS` + `ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING` |

## 重置到干净状态

```bash
docker compose down --volumes --remove-orphans
```

再次 `up -d` 即重新初始化。

## 文件级别约定

- 改动 schema 时：优先在 `auto/schemas/` 对应文件里改；因为全部用 `IF NOT EXISTS`，已存在的表不会被覆盖，需要变更现有表结构时手动 drop 重建或写 `ALTER` 脚本。
- 新增业务模块 schema 时：在 `aim-schema.sql` 中追加 `CREATE SCHEMA IF NOT EXISTS xxx` + 对应的 `CREATE TABLE`。
- 新增 Nacos 配置 DataId 时：在 `init/nacos-init-data.sql` 的 DO 块里追加对应的 INSERT。