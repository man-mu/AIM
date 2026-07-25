# file-service 测试优化指导说明

> **目标**：建立分层测试体系，补充纯单元测试覆盖业务逻辑盲区，改进现有集成测试的隔离性与健壮性。
> **现状**：仅有 1 个集成测试类 `FileServiceImplTest`（22 用例），依赖 PostgreSQL + MinIO 真实运行，无纯单元测试。

---

## 0. 分层测试策略

| 层级 | 类型       | 依赖                          | 速度   | 定位                   |
| ---- | ---------- | ----------------------------- | ------ | ---------------------- |
| L1   | 纯单元测试 | 无 Spring 容器，Mock 外部依赖 | 毫秒级 | 验证业务逻辑分支覆盖   |
| L2   | 集成测试   | Spring Boot + PG + MinIO      | 秒级   | 验证组件协作与真实交互 |

### 设计决策

| #    | 决策项        | 选择                                                         | 理由                                                         |
| ---- | ------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1    | 测试框架      | JUnit 5 + Mockito                                            | JUnit 5 由 `spring-boot-starter-test` 携带；Mockito 同包自带，无需额外依赖 |
| 2    | L1 调用方式   | `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks` | 纯单元测试不启动 Spring 容器，Mock 所有外部依赖              |
| 3    | L2 调用方式   | `@SpringBootTest` + 构造器注入 `@Autowired`                  | 集成测试验证真实交互；构造器注入守反字段注入规范             |
| 4    | L2 Dubbo 隔离 | 专用 `TestConfig` + exclude Dubbo 自动配置                   | 避开 `@EnableDubbo`，测试不连 Nacos / 不占端口               |
| 5    | L2 测试实例   | `@TestInstance(Lifecycle.PER_CLASS)`                         | 允许 `@AfterAll` 非静态访问实例字段做清理                    |
| 6    | L2 数据清理   | `@AfterAll` 硬删 `deleteBatchIds`                            | PENDING 行会污染表且被 zombie 清理器误删；统一物理删除幂等可重复执行 |
| 7    | L1 Mock 策略  | Mock MinioClient + FileMapper + SnowflakeIdWorker            | 隔离基础设施，专注业务逻辑                                   |
| 8    | L2 Mock 策略  | 不 Mock，全部真实环境                                        | 集成测试的意义就是验证真实交互                               |

---

## 1. 依赖说明

### 1.1 已有依赖（无需修改）

`spring-boot-starter-test` 已包含 JUnit 5 + Mockito + AssertJ + Spring Boot Test，L1/L2 测试均可直接使用。

### 1.2 L2 集成测试 application.yml

`file-service/src/test/resources/application.yml` 已存在，内容无需修改。其核心作用是阻止 Dubbo 的 `DubboApplicationContextInitializer` 自动生成 stub 覆盖 main 配置。

> **不要**在此 yml 写 `dubbo.*` 任何配置——Dubbo 自动配置已被 `TestConfig.exclude` 排除，写了反而可能触发 Dubbo 启动。

---

## 2. 现有问题分析

### 2.1 核心问题

| #    | 问题                                  | 严重度 | 说明                                                         |
| ---- | ------------------------------------- | ------ | ------------------------------------------------------------ |
| 1    | 名不副实：集成测试充当单元测试        | 🔴 高   | `FileServiceImplTest` 使用 `@SpringBootTest`，依赖 PG + MinIO，本质是集成测试，速度慢、环境脆弱 |
| 2    | 测试间强顺序依赖                      | 🔴 高   | `@Order(1)`~`@Order(11)` 共享 `fileId` 实例变量，前置失败导致后续连锁失败 |
| 3    | `cleanupZombieFiles()` 零覆盖         | 🔴 高   | zombie 清理逻辑包含时间阈值计算、MinIO best-effort 删除、DB 硬删除，无任何测试 |
| 4    | `FileValidator` 无独立单元测试        | 🟡 中   | 纯静态工具类，零依赖，最适合快速单测，当前仅被集成测试间接覆盖部分分支 |
| 5    | `deleteFile` PENDING→DELETED 未测试   | 🟡 中   | 业务设计支持"取消上传"，但测试只覆盖 CONFIRMED→DELETED       |
| 6    | `confirmUpload` MD5 逻辑未测试        | 🟡 中   | MD5 更新 / 不一致仅日志不阻断，均无覆盖                      |
| 7    | `batchGetFileInfo` 边界不足           | 🟢 低   | 缺"全部不存在"、"PENDING 被过滤"、"DELETED 被过滤"场景       |
| 8    | `getDownloadURL` 返回 file 字段未断言 | 🟢 低   | 只验证了 URL 和 expiresAt                                    |

### 2.2 覆盖率盲区

| 被测类              | 方法                 | 当前覆盖 | 缺失分支                       |
| ------------------- | -------------------- | -------- | ------------------------------ |
| `FileServiceImpl`   | `getUploadURL`       | ✅ 充分   | —                              |
| `FileServiceImpl`   | `confirmUpload`      | ⚠️ 部分   | MD5 更新、MD5 不一致日志       |
| `FileServiceImpl`   | `getDownloadURL`     | ✅ 较好   | resp.file 字段断言             |
| `FileServiceImpl`   | `getFileInfo`        | ✅        | —                              |
| `FileServiceImpl`   | `batchGetFileInfo`   | ⚠️ 部分   | 全不存在、PENDING/DELETED 过滤 |
| `FileServiceImpl`   | `deleteFile`         | ⚠️ 部分   | PENDING→DELETED                |
| `FileServiceImpl`   | `cleanupZombieFiles` | ❌ 零覆盖 | 全部                           |
| `FileValidator`     | `validateSize`       | ⚠️ 间接   | size≤0、purpose=3/4            |
| `FileValidator`     | `validateMimeType`   | ⚠️ 间接   | null/empty mimeType            |
| `FileValidator`     | `safeExtractExt`     | ✅ 间接   | ext 超 16 字符、ext 含特殊字符 |
| `FileValidator`     | `buildObjectKey`     | ❌ 零覆盖 | 日期格式 + 路径拼接            |
| `ZombieFileCleaner` | `cleanup`            | ❌ 零覆盖 | 调度 + 异常兜底                |

---

## 3. 优化方案：新增 L1 纯单元测试

### 3.1 FileValidatorTest

**路径**：`file-service/src/test/java/lanshan/manmu/file/util/FileValidatorTest.java`

**特点**：纯 JUnit 5，无 Spring 容器，毫秒级执行。

| #    | 测试方法                                  | 场景                                    | 预期                                |
| ---- | ----------------------------------------- | --------------------------------------- | ----------------------------------- |
| 1    | `shouldRejectSizeZeroOrNegative`          | size ≤ 0                                | BAD_REQUEST                         |
| 2    | `shouldAcceptAttachmentWithinLimit`       | purpose=1, size ≤ 100MB                 | 不抛异常                            |
| 3    | `shouldRejectOversizeAttachment`          | purpose=1, size > 100MB                 | FILE_TOO_LARGE                      |
| 4    | `shouldAcceptAvatarWithinLimit`           | purpose=2, size ≤ 50MB                  | 不抛异常                            |
| 5    | `shouldRejectOversizeAvatar`              | purpose=2, size > 50MB                  | FILE_TOO_LARGE                      |
| 6    | `shouldUseAttachmentLimitForOtherPurpose` | purpose=3/4/0, size > 100MB             | FILE_TOO_LARGE                      |
| 7    | `shouldRejectNullMimeType`                | mimeType = null                         | FILE_TYPE_NOT_SUPPORT               |
| 8    | `shouldRejectEmptyMimeType`               | mimeType = ""                           | FILE_TYPE_NOT_SUPPORT               |
| 9    | `shouldAcceptImageMime`                   | image/jpeg                              | 不抛异常                            |
| 10   | `shouldAcceptVideoMime`                   | video/mp4                               | 不抛异常                            |
| 11   | `shouldAcceptPdfMime`                     | application/pdf                         | 不抛异常                            |
| 12   | `shouldRejectExeMime`                     | application/x-msdownload                | FILE_TYPE_NOT_SUPPORT               |
| 13   | `shouldExtractJpgExt`                     | name="photo.jpg"                        | "jpg"                               |
| 14   | `shouldExtractPngExt`                     | name="icon.PNG"                         | "png"（小写化）                     |
| 15   | `shouldReturnBinForNoExtension`           | name="noext"                            | "bin"                               |
| 16   | `shouldReturnBinForTrailingDot`           | name="file."                            | "bin"                               |
| 17   | `shouldReturnBinForSpecialCharExt`        | name="file.sh@"                         | "bin"                               |
| 18   | `shouldReturnBinForOverlongExt`           | name="file.aaaaaaaaaaaaaaaaa"（17字符） | "bin"                               |
| 19   | `shouldReturnBinForNullName`              | name=null                               | "bin"                               |
| 20   | `shouldReturnBinForEmptyName`             | name=""                                 | "bin"                               |
| 21   | `shouldBuildObjectKeyWithDate`            | fileId=123, ext="jpg"                   | key 匹配 `files/yyyy-MM-dd/123.jpg` |
| 22   | `shouldBuildObjectKeyWithBinExt`          | fileId=456, ext="bin"                   | key 以 `/456.bin` 结尾              |

### 3.2 FileServiceImplUnitTest

**路径**：`file-service/src/test/java/lanshan/manmu/file/service/impl/FileServiceImplUnitTest.java`

**特点**：`@ExtendWith(MockitoExtension.class)`，Mock `MinioClient` + `FileMapper` + `SnowflakeIdWorker`，专注业务逻辑分支。

| #    | 测试方法                                         | 场景                     | Mock 设定                                     | 预期                                       |
| ---- | ------------------------------------------------ | ------------------------ | --------------------------------------------- | ------------------------------------------ |
| 1    | `shouldGetUploadURL`                             | 正常流程                 | snowflake→123L, minio→URL, mapper.insert 成功 | fileId=123, key 含日期, expiresAt 正确     |
| 2    | `shouldRejectNullName`                           | name=null                | —                                             | BAD_REQUEST                                |
| 3    | `shouldRejectEmptyName`                          | name=""                  | —                                             | BAD_REQUEST                                |
| 4    | `shouldHandleMinioErrorOnUpload`                 | MinIO 抛异常             | minio 抛 Exception                            | FILE_UPLOAD_FAILED                         |
| 5    | `shouldConfirmUpload`                            | 正常确认                 | mapper→PENDING entity                         | status→CONFIRMED, mapper.updateById 被调用 |
| 6    | `shouldRejectConfirmByNonUploader`               | uploaderId 不匹配        | mapper→entity(uploaderId=其他)                | FILE_NOT_UPLOADER                          |
| 7    | `shouldRejectConfirmTwice`                       | status=CONFIRMED         | mapper→CONFIRMED entity                       | BAD_REQUEST                                |
| 8    | `shouldConfirmUploadWithMd5`                     | 传 MD5                   | mapper→PENDING entity                         | entity.md5 被设置                          |
| 9    | `shouldLogMd5MismatchButNotBlock`                | MD5 不一致               | mapper→entity(md5="abc")                      | 不抛异常，仅 warn 日志                     |
| 10   | `shouldRejectConfirmNotFound`                    | fileId 不存在            | mapper→null                                   | FILE_NOT_FOUND                             |
| 11   | `shouldGetDownloadURL`                           | 正常下载                 | mapper→CONFIRMED entity, minio→URL            | downloadUrl + file 字段正确                |
| 12   | `shouldRejectDownloadPending`                    | status=PENDING           | mapper→PENDING entity                         | FILE_PENDING                               |
| 13   | `shouldRejectDownloadDeleted`                    | status=DELETED           | mapper→DELETED entity                         | FILE_DELETED                               |
| 14   | `shouldHandleMinioErrorOnDownload`               | MinIO 抛异常             | mapper→CONFIRMED, minio 抛 Exception          | INTERNAL_ERROR                             |
| 15   | `shouldGetFileInfo`                              | 正常查询                 | mapper→CONFIRMED entity                       | FileInfo 字段映射正确                      |
| 16   | `shouldRejectGetFileInfoNotFound`                | fileId 不存在            | mapper→null                                   | FILE_NOT_FOUND                             |
| 17   | `shouldRejectGetFileInfoPending`                 | status=PENDING           | mapper→PENDING entity                         | FILE_PENDING                               |
| 18   | `shouldBatchGetFileInfo`                         | 混合状态                 | mapper→[CONFIRMED, PENDING, DELETED]          | 只返回 CONFIRMED                           |
| 19   | `shouldBatchGetFileInfoReturnEmptyForEmptyList`  | 空列表                   | —                                             | 空 List                                    |
| 20   | `shouldBatchGetFileInfoReturnEmptyIfAllNotFound` | 全不存在                 | mapper→[]                                     | 空 List                                    |
| 21   | `shouldDeleteFile`                               | 正常删除 CONFIRMED       | mapper→CONFIRMED entity                       | status→DELETED, minio.removeObject 被调用  |
| 22   | `shouldDeletePendingFile`                        | 删除 PENDING（取消上传） | mapper→PENDING entity                         | status→DELETED                             |
| 23   | `shouldRejectDeleteByNonUploader`                | uploaderId 不匹配        | mapper→entity(uploaderId=其他)                | FILE_NOT_UPLOADER                          |
| 24   | `shouldRejectDeleteTwice`                        | status=DELETED           | mapper→DELETED entity                         | FILE_DELETED                               |
| 25   | `shouldRejectDeleteNotFound`                     | fileId 不存在            | mapper→null                                   | FILE_NOT_FOUND                             |
| 26   | `shouldTolerateMinioErrorOnDelete`               | MinIO 删除失败           | mapper→CONFIRMED, minio 抛异常                | 不抛异常（best-effort），DB 仍软删         |
| 27   | `shouldCleanupZombieFiles`                       | 有超时 PENDING 记录      | mapper→[zombie entity], minio 成功            | mapper.deleteById 被调用                   |
| 28   | `shouldCleanupZombieFilesSkipWhenNone`           | 无 zombie                | mapper→[]                                     | 不调用 deleteById                          |
| 29   | `shouldCleanupZombieFilesTolerateMinioError`     | MinIO 删除失败           | mapper→[zombie], minio 抛异常                 | 仍调用 deleteById（DB 照删）               |

### 3.3 ZombieFileCleanerTest

**路径**：`file-service/src/test/java/lanshan/manmu/file/scheduler/ZombieFileCleanerTest.java`

**特点**：`@ExtendWith(MockitoExtension.class)`，Mock `FileService`，验证调度器层异常兜底。

| #    | 测试方法                      | 场景               | 预期                                    |
| ---- | ----------------------------- | ------------------ | --------------------------------------- |
| 1    | `shouldDelegateToFileService` | 正常调用           | fileService.cleanupZombieFiles() 被调用 |
| 2    | `shouldSwallowException`      | fileService 抛异常 | 不向上抛出（日志兜底）                  |

---

## 4. 优化方案：改进 L2 集成测试

### 4.1 重命名

`FileServiceImplTest` → `FileServiceImplIntegrationTest`，明确其集成测试定位。

### 4.2 消除测试间顺序依赖

**当前问题**：`@Order(1)`~`@Order(11)` 共享 `fileId`，前置失败导致后续连锁失败。

**改进方案**：将主链路合并为单个 `@Test` 方法，在方法内部顺序执行 getUploadURL → confirm → download → getFileInfo → batchGetFileInfo → delete 全流程。这样既保留了端到端验证的价值，又消除了跨方法的状态耦合。

独立异常用例（`@Order(12)`~`@Order(22)`）本身无顺序依赖，可保持为独立 `@Test` 方法。

### 4.3 补充缺失场景

| #    | 新增用例                                         | 场景                                   | 预期                 |
| ---- | ------------------------------------------------ | -------------------------------------- | -------------------- |
| 1    | `shouldDeletePendingFile`                        | uploader 删除 PENDING 文件（取消上传） | status → DELETED     |
| 2    | `shouldConfirmWithMd5`                           | confirm 传 MD5                         | DB entity.md5 被更新 |
| 3    | `shouldBatchGetFileInfoFilterPending`            | batch 含 PENDING 文件                  | PENDING 被过滤       |
| 4    | `shouldBatchGetFileInfoFilterDeleted`            | batch 含 DELETED 文件                  | DELETED 被过滤       |
| 5    | `shouldBatchGetFileInfoReturnEmptyIfAllNotFound` | batch 全不存在 ID                      | 返回空 List          |

### 4.4 改进断言

- `shouldGetDownloadURL`：补充 `resp.getFile()` 字段断言（fileId、name、status）
- 时间相关断言：引用 `CommonConst.FILE_PRESIGN_EXPIRE_SEC` 常量替代硬编码 `60_000`

---

## 5. 实施优先级

| 优先级 | 任务                                                    | 工作量 | 价值                                     |
| ------ | ------------------------------------------------------- | ------ | ---------------------------------------- |
| P0     | 新建 `FileValidatorTest`                                | 小     | 填补最大盲区，纯单测秒级执行             |
| P0     | 新建 `FileServiceImplUnitTest`（含 cleanupZombieFiles） | 中     | 覆盖零覆盖方法 + MD5 分支 + PENDING 删除 |
| P1     | 集成测试重命名 + 消除顺序依赖                           | 中     | 提升测试健壮性                           |
| P1     | 集成测试补充缺失场景                                    | 小     | 补齐边界                                 |
| P2     | 新建 `ZombieFileCleanerTest`                            | 小     | 调度器异常兜底                           |
| P2     | 集成测试改进断言                                        | 小     | 提升断言深度                             |

---

## 6. 执行命令

### 6.1 L1 纯单元测试（无需基础设施）

```bash
# 运行 FileValidatorTest
mvn test -pl file-service -am -Dtest=FileValidatorTest -Dsurefire.failIfNoSpecifiedTests=false

# 运行 FileServiceImplUnitTest
mvn test -pl file-service -am -Dtest=FileServiceImplUnitTest -Dsurefire.failIfNoSpecifiedTests=false

# 运行 ZombieFileCleanerTest
mvn test -pl file-service -am -Dtest=ZombieFileCleanerTest -Dsurefire.failIfNoSpecifiedTests=false

# 运行全部 L1 单元测试
mvn test -pl file-service -am -Dtest="FileValidatorTest,FileServiceImplUnitTest,ZombieFileCleanerTest" -Dsurefire.failIfNoSpecifiedTests=false
```

### 6.2 L2 集成测试（需 PostgreSQL + MinIO）

```bash
# 1. 确保基础设施运行
docker compose up -d

# 2. 执行 DB schema（首次或 schema 变更后）
docker exec -i aim-postgres psql -U postgres -d aim -f /dev/stdin < backend/docs/sql/aim-schema.sql

# 3. 运行集成测试
mvn test -pl file-service -am -Dtest=FileServiceImplIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

# 4. 只运行单个测试方法
mvn test -pl file-service -am -Dtest=FileServiceImplIntegrationTest#shouldGetUploadURL -Dsurefire.failIfNoSpecifiedTests=false
```

> **`-am`**：also make 会同时编译 `aim-common`，否则若 common 尚未 `mvn install` 进本地 `~/.m2`，`-pl file-service` 会因依赖解析失败。
> **`-Dsurefire.failIfNoSpecifiedTests=false`**：`-am` 会把 common 也带进测试 reactor；common 中没有匹配的测试类，不加该 flag 会报"未找到指定测试"。

---

## 7. 注意事项

### 7.1 L1 单元测试

1. **不启动 Spring 容器**：`@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`，毫秒级执行
2. **Mock 边界**：Mock `MinioClient`、`FileMapper`、`SnowflakeIdWorker`；**不 Mock** `FileValidator`（纯逻辑，应真实调用）
3. **BizException.getCode() 返回 int**：断言时用 `ErrorCode.XXX.getCode()` 而非 `ErrorCode.XXX`
4. **MinIO Mock 要抛 checked Exception**：`getPresignedObjectUrl` 抛 `ErrorResponseException` 等，Mock 时用 `thenThrow`
5. **cleanupZombieFiles 测试要点**：验证 `LambdaQueryWrapper` 条件（PENDING + 超时）、MinIO best-effort（失败不阻断 DB 删除）、DB 硬删除（非软删）

### 7.2 L2 集成测试

1. **基础环境**：PostgreSQL + MinIO 必须运行；Nacos / Dubbo 因被隔离**不再依赖**（`TestConfig` 已 exclude）
2. **构造器注入守规范 + 必须加 @Autowired**：JUnit 5 + `@SpringBootTest` 默认 FIELD 模式，构造器注入需显式 `@Autowired`
3. **DB 清理**：`@AfterAll cleanup()` 硬删 `createdFileIds` 所有行；DELETED 软删行也会被 `deleteBatchIds` 物理清除
4. **PENDING 行风险**：独立异常用例会留 PENDING 行，全程运行远短于 30min，不会被 zombie 清理器误删；即便意外误删 @AfterAll 仍能安全 `deleteBatchIds`
5. **MinIO 对象不真实上传**：测试只生成 Presigned URL 不真正 PUT 对象，`deleteFile` 里 `minioClient.removeObject` 去删不存在的对象——MinIO 对此静默返回成功，不影响断言
6. **重复运行幂等**：`fileId` 是 Snowflake 生成，每次运行不重复触发唯一键冲突；加上 `@AfterAll` 硬删，可无限重复执行
7. **Dubbo 隔离关键点**：
    - **TestConfig 不继承 FileServiceApplication**：避开启动类上的 `@EnableDubbo`
    - **exclude Dubbo 自动配置**：`DubboAutoConfiguration` / `DubboListenerAutoConfiguration` / `DubboRelaxedBinding2AutoConfiguration` / `DubboTripleAutoConfiguration` 共 4 个
    - **excludeFilters 跳过 rpc 包**：`@DubboService` 注解会触发 Dubbo 启动
8. **测试 application.yml 不可删除**：Dubbo 的 `DubboApplicationContextInitializer` 在检测到 `target/test-classes/application.yml` 不存在时会自动生成 stub，覆盖 main 配置导致数据源失配