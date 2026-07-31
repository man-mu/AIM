# file-service 详细实现 spec

> **目标**：实现文件管理服务的完整业务代码，达到生产可用标准。
> **学习要点**：Dubbo Provider 暴露、MyBatis-Plus CRUD、MinIO Presigned URL 两阶段上传、文件安全校验、zombie 定时清理。
> **前置条件**：L0 common 模块完成、L1 user-service 完成（含 A/B/C/D 修复）。

---

## 0. 设计决策清单（已确认）

| # | 决策项 | 值 | 理由 |
|---|---|---|---|
| 1 | MinIO bucket 策略 | **私有** + Presigned GET URL | 生产标准，下载必须走预签名 |
| 2 | 上传流程 | **预分配 fileId** + 两阶段 + zombie 清理 | MAIM 方案，客户端直传 MinIO |
| 3 | 图片处理 | **不做** | Phase 1 纯文件流转，width/height/duration 始终为 0 |
| 4 | 下载访问控制 | **Phase 1 校验身份有效 + 文件 CONFIRMED**；Phase 2 接入 ConvRpcService | L1 叶子服务不能调 conv-service |
| 5 | 删除访问控制 | **仅 uploader 可删** | 写操作需归属校验 |
| 6 | 时间类型 | **OffsetDateTime** | 与已修复的 user-service 一致，匹配 PG TIMESTAMPTZ |
| 7 | PG 连接 | `?currentSchema=file` | file 不是 PG 保留字，不需要双引号 |
| 8 | objectKey 格式 | `files/{yyyy-MM-dd}/{fileId}.{safeExt}` | ext 安全过滤：正则 `[a-zA-Z0-9]+$` 转小写，无匹配则 `.bin` |
| 9 | DB schema | **加 status 字段**：PENDING=0, CONFIRMED=1, DELETED=2 | 两阶段上传 + zombie 清理 |
| 10 | 文件限制 | 图片 ≤ 50MB，附件 ≤ 100MB + MIME 白名单 + ext 过滤 | 排除可执行文件 |
| 11 | expiresIn | **服务端固定 1800 秒**，忽略客户端传值 | 安全边界服务端控制 |
| 12 | zombie 清理 | PENDING 超 30 分钟 + `@Scheduled(fixedDelay = 300_000)` 每 5 分钟扫一次 | 与 Presigned URL 有效期对齐 |
| 13 | MD5 校验 | Phase 1 **仅记录日志不阻断** | MinIO 分片上传 ETag ≠ 文件 MD5，避免误杀 |

---

## 1. MinIO Java SDK API 验证（已反编译确认）

> 以下 API 签名通过 `javap` 反编译 `minio-8.5.14.jar` 验证，非凭印象编写。

### 1.1 MinioClient 构建

```java
// io.minio.MinioClient.Builder
MinioClient.builder()
    .endpoint(String endpoint)          // 如 "http://localhost:9000"
    .credentials(String accessKey, String secretKey)
    .build()                            // → MinioClient
```

### 1.2 Bucket 操作

```java
// 检查 bucket 是否存在 → boolean
minioClient.bucketExists(
    BucketExistsArgs.builder().bucket("aim").build()
)

// 创建 bucket → void
minioClient.makeBucket(
    MakeBucketArgs.builder().bucket("aim").build()
)
```

### 1.3 Presigned URL 生成

```java
// 生成预签名 URL → String
// Method 枚举：io.minio.http.Method.GET / PUT / POST / DELETE / HEAD
minioClient.getPresignedObjectUrl(
    GetPresignedObjectUrlArgs.builder()
        .method(Method.PUT)             // 上传用 PUT，下载用 GET
        .bucket("aim")
        .object("files/2026-07-16/123.jpg")
        .expiry(1800, TimeUnit.SECONDS) // 过期时间
        .build()
)
```

> **注意**：MinIO SDK 8.5.14 的方法名是 `getPresignedObjectUrl`，不是 `presignedPutObject` / `presignedGetObject`。PUT 和 GET 都用同一个方法，通过 `Method` 参数区分。

### 1.4 删除对象

```java
// 删除 MinIO 对象 → void
minioClient.removeObject(
    RemoveObjectArgs.builder()
        .bucket("aim")
        .object("files/2026-07-16/123.jpg")
        .build()
)
```

---

## 2. DB Schema 变更

### 2.1 ALTER TABLE（需手动执行）

```sql
-- 给 file.files 表加 status 字段
ALTER TABLE file.files ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 0;

-- 给 status 加索引（zombie 清理查询用：WHERE status=0 AND created_at < xxx）
CREATE INDEX IF NOT EXISTS idx_files_status_created
    ON file.files(status, created_at)
    WHERE status = 0;
```

### 2.2 变更后的完整表结构

```sql
CREATE TABLE IF NOT EXISTS file.files (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(512) NOT NULL DEFAULT '',
    key         VARCHAR(512) NOT NULL DEFAULT '',
    size        BIGINT       NOT NULL DEFAULT 0,
    mime_type   VARCHAR(256) NOT NULL DEFAULT '',
    ext         VARCHAR(32)  NOT NULL DEFAULT '',
    width       INT          NOT NULL DEFAULT 0,    -- Phase 1 始终 0
    height      INT          NOT NULL DEFAULT 0,    -- Phase 1 始终 0
    duration    INT          NOT NULL DEFAULT 0,    -- Phase 1 始终 0
    md5         VARCHAR(64)  NOT NULL DEFAULT '',
    purpose     SMALLINT     NOT NULL DEFAULT 0,    -- 1=消息附件 2=头像 3=文档 4=媒体
    access      SMALLINT     NOT NULL DEFAULT 0,    -- 1=私有 2=会话可见 3=公开
    uploader_id BIGINT       NOT NULL DEFAULT 0,
    bucket      VARCHAR(128) NOT NULL DEFAULT 'aim',
    status      SMALLINT     NOT NULL DEFAULT 0,    -- 0=PENDING 1=CONFIRMED 2=DELETED
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

> **注意**：`aim-schema.sql` 中的 `IF NOT EXISTS` 保证幂等，但 `ALTER TABLE ADD COLUMN IF NOT EXISTS` 是 PG 9.6+ 特性，PG 16 支持。

### 2.3 索引清单

| 索引名 | 字段 | 用途 |
|---|---|---|
| `idx_files_uploader` | `uploader_id` | deleteFile 校验上传者 |
| `idx_files_key` | `key` | MinIO 对象删除时按 key 查 |
| `idx_files_status_created` | `status, created_at WHERE status=0` | zombie 清理查询 |

---

## 3. common 模块改动

### 3.1 ErrorCode 追加

```java
// file-service 5xxxx — 追加
FILE_PENDING           (50005, "文件尚未上传确认"),
FILE_DELETED           (50006, "文件已删除"),
FILE_NOT_UPLOADER      (50007, "无权操作他人文件"),
FILE_NAME_INVALID      (50008, "文件名不合法"),
```

### 3.2 CommonConst 追加

```java
// 文件状态
public static final int FILE_STATUS_PENDING   = 0;
public static final int FILE_STATUS_CONFIRMED = 1;
public static final int FILE_STATUS_DELETED   = 2;

// 文件大小限制（按 purpose 区分）
public static final long FILE_MAX_SIZE_IMAGE     = 50L * 1024 * 1024;   // 图片 ≤ 50MB
public static final long FILE_MAX_SIZE_ATTACHMENT = 100L * 1024 * 1024;  // 附件 ≤ 100MB

// Presigned URL 有效期（服务端固定，忽略客户端传值）
public static final int FILE_PRESIGN_EXPIRE_SEC = 1800;  // 30 分钟

// Zombie 清理
public static final int FILE_ZOMBIE_TTL_MINUTES = 30;       // PENDING 超 30 分钟视为 zombie
public static final long FILE_ZOMBIE_SCAN_INTERVAL_MS = 300_000L;  // 5 分钟扫一次
```

> **保留**原有的 `MAX_FILE_SIZE` 常量不动（其他模块可能引用，遵循"精准手术"原则），`FILE_MAX_SIZE_IMAGE` / `FILE_MAX_SIZE_ATTACHMENT` 为 file-service 专用补充常量。

### 3.3 FileInfo DTO 追加 status 字段

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private long fileId;
    private String name;
    private String key;
    private long size;
    private String mimeType;
    private String ext;
    private int width;
    private int height;
    private int duration;
    private String md5;
    private int purpose;
    private int access;
    private long uploaderId;
    private String bucket;
    private int status;          // 新增：0=PENDING 1=CONFIRMED 2=DELETED
    private long createdAt;
}
```

---

## 4. file-service pom.xml

### 4.1 当前依赖

```
aim-common, spring-boot-starter, dubbo-spring-boot-starter, minio, nacos-discovery, lombok
```

### 4.2 需补充

```xml
<!-- MyBatis-Plus + PostgreSQL（file-service 需要写 DB） -->
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
```

> **不需要** `spring-security-crypto`（file-service 无密码哈希需求）。
> **不需要** `spring-boot-starter-data-redis`（file-service 不用 Redis）。
> **需要** `spring-boot-starter-web`（spec-rev: 架构 1 双协议暴露，file-service 同时暴露 HTTP 8083 + Dubbo 20884，HTTP Controller 规范见 [controller-spec.md](./controller-spec.md)）。

---

## 5. application.yml

```yaml
spring:
  application:
    name: file-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/aim?currentSchema=file
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848

dubbo:
  application:
    name: file-service
  protocol:
    name: dubbo
    port: 20886
  registry:
    address: nacos://localhost:8848

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

aim:
  snowflake:
    worker-id: 4

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:aim}
```

> **与 user-service 对齐**：DB/MinIO 密码均用 `${ENV:default}` 环境变量注入。
> **注意**：`currentSchema=file` 不需要双引号（file 不是 PG 保留字）。

---

## 6. 启动类

```java
@SpringBootApplication
@EnableDubbo
@EnableScheduling
@MapperScan("lanshan.manmu.file.mapper")
public class FileServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
```

> `@EnableScheduling` 激活定时任务（zombie 清理）。
> `@MapperScan` 只在启动类声明一次，**不在 Config 类里重复**（user-service 的 B8 教训）。

---

## 7. Entity

### `model/entity/FileEntity.java`

```java
package lanshan.manmu.file.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("files")
public class FileEntity {

    @TableId
    private Long id;
    private String name;
    private String key;            // MinIO object key
    private Long size;             // 字节
    private String mimeType;
    private String ext;            // 安全过滤后的扩展名，如 "jpg"
    private Integer width;         // Phase 1 始终 0
    private Integer height;        // Phase 1 始终 0
    private Integer duration;      // Phase 1 始终 0
    private String md5;
    private Integer purpose;       // 1=消息附件 2=头像 3=文档 4=媒体
    private Integer access;        // 1=私有 2=会话可见 3=公开
    private Long uploaderId;
    private String bucket;
    private Integer status;        // 0=PENDING 1=CONFIRMED 2=DELETED
    private OffsetDateTime createdAt;
}
```

**字段追踪**：

| Entity 字段 | 谁写入 | 谁读取 | 备注 |
|---|---|---|---|
| id | getUploadURL (snowflake) | 所有方法 | 主键 |
| name | getUploadURL (req.name) | toFileInfo | 原始文件名 |
| key | getUploadURL (生成) | deleteFile (MinIO 删除) | 安全生成的 objectKey |
| size | getUploadURL (req.size) | toFileInfo | 字节数 |
| mimeType | getUploadURL (req.mimeType) | toFileInfo | 经白名单校验 |
| ext | getUploadURL (安全过滤) | toFileInfo | 防路径穿越 |
| width/height/duration | 不写入（Phase 1 为 0） | toFileInfo | 始终 0 |
| md5 | confirmUpload (req.md5) | toFileInfo | Phase 1 仅日志 |
| purpose | getUploadURL (req.purpose) | getUploadURL (大小限制判断) | 区分图片/附件 |
| access | getUploadURL (req.access) | toFileInfo | Phase 1 不用于下载校验 |
| uploaderId | getUploadURL (req.uploaderId) | deleteFile (归属校验) | |
| bucket | getUploadURL (配置值) | deleteFile (MinIO 删除) | 默认 "aim" |
| status | getUploadURL(0) / confirmUpload(1) / deleteFile(2) | getDownloadURL / getFileInfo / zombieCleaner | 核心状态机 |
| createdAt | getUploadURL (OffsetDateTime.now()) | zombieCleaner | zombie 判断依据 |

---

## 8. Mapper

### `mapper/FileMapper.java`

```java
package lanshan.manmu.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.file.model.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {
}
```

> MyBatis-Plus `BaseMapper` 提供：insert / updateById / selectById / selectBatchIds / selectList / deleteById 等。
> **不需要自定义 SQL**——zombie 清理用 `selectList` + `LambdaQueryWrapper` 即可。

---

## 9. Config 类

### 9.1 `config/MybatisPlusConfig.java`

```java
package lanshan.manmu.file.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（分页插件）。
 * 注意：@MapperScan 在启动类声明，此处不重复。
 */
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

> **对比 user-service B8 教训**：`@MapperScan` 只在 `FileServiceApplication` 上声明，此处不放。

### 9.2 `config/MinioConfig.java`

```java
package lanshan.manmu.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 * 启动时自动检查 bucket 是否存在，不存在则创建。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 启动时检查 bucket，不存在则创建
        try {
            if (!client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' 创建成功", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败，bucket={}, endpoint={}", bucket, endpoint, e);
            throw new IllegalStateException("MinIO 初始化失败", e);
        }

        return client;
    }
}
```

**设计要点**：
- `@Slf4j` + `log.error`——启动失败必须打日志（user-service B3 教训：不允许吞异常）
- bucket 不存在时自动创建，生产环境可改为只检查不创建
- 初始化失败抛 `IllegalStateException` 阻止 Spring 启动——**fail fast**

### 9.3 `config/SnowflakeConfig.java`

```java
package lanshan.manmu.file.config;

import lanshan.manmu.common.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Value("${aim.snowflake.worker-id:4}")
    private long workerId;

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId);
    }
}
```

> **YAGNI**：不创建 `RedisConfig`（file-service 不用 Redis）、不创建 `RedisTemplate` Bean（user-service B9 教训）。

---

## 10. FileValidator 工具类

### `util/FileValidator.java`

```java
package lanshan.manmu.file.util;

import java.util.Set;
import lanshan.manmu.common.constant.CommonConst;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;

/**
 * 文件安全校验器。
 * <ul>
 *   <li>文件名安全过滤：防路径穿越，提取合法扩展名</li>
 *   <li>MIME 白名单：排除可执行文件</li>
 *   <li>大小限制：按 purpose 区分图片/附件</li>
 * </ul>
 */
public final class FileValidator {

    // MIME 白名单前缀 + 精确匹配
    private static final Set<String> MIME_PREFIX_WHITELIST = Set.of(
            "image/", "video/", "audio/"
    );
    private static final Set<String> MIME_EXACT_WHITELIST = Set.of(
            "application/pdf",
            "text/plain",
            "application/zip",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    // 合法扩展名正则：仅允许字母+数字，1~16 字符
    private static final java.util.regex.Pattern EXT_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9]{1,16}$");

    /**
     * 校验文件大小。
     * @param size 文件大小（字节）
     * @param purpose 文件用途：1=消息附件 2=头像 3=文档 4=媒体
     *                注：purpose 由客户端传入，不可信；仅 purpose=2(头像) 按图片限制 50MB，
     *                其余（含 purpose=0 默认值 / purpose=4 媒体）按附件限制 100MB
     */
    public static void validateSize(long size, int purpose) {
        if (size <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小必须大于 0");
        }
        long maxSize = CommonConst.FILE_MAX_SIZE_ATTACHMENT;
        if (purpose == 2) {
            maxSize = CommonConst.FILE_MAX_SIZE_IMAGE;
        }
        if (size > maxSize) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小 " + size + " 超过限制 " + maxSize);
        }
    }

    /**
     * 校验 MIME 类型是否在白名单内。
     */
    public static void validateMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "MIME 类型为空");
        }
        for (String prefix : MIME_PREFIX_WHITELIST) {
            if (mimeType.startsWith(prefix)) return;
        }
        if (MIME_EXACT_WHITELIST.contains(mimeType)) return;
        throw new BizException(ErrorCode.FILE_TYPE_NOT_SUPPORT,
                "不支持的 MIME 类型: " + mimeType);
    }

    /**
     * 从文件名中安全提取扩展名。
     * <p>防路径穿越：剔除 / \ .. 等字符，仅保留末尾合法扩展名。
     * @param name 原始文件名（不可信，来自客户端）
     * @return 安全的扩展名（小写，无点号），如 "jpg"；无合法扩展名返回 "bin"
     */
    public static String safeExtractExt(String name) {
        if (name == null || name.isEmpty()) {
            return "bin";
        }
        // 取最后一个 '.' 之后的部分
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "bin";
        }
        String rawExt = name.substring(dotIndex + 1);
        // 仅允许字母+数字，防注入
        if (!EXT_PATTERN.matcher(rawExt).matches()) {
            return "bin";
        }
        return rawExt.toLowerCase();
    }

    /**
     * 生成 MinIO objectKey。
     * <p>格式：files/{yyyy-MM-dd}/{fileId}.{ext}
     * @param fileId Snowflake ID
     * @param ext 安全过滤后的扩展名（无点号）
     */
    public static String buildObjectKey(long fileId, String ext) {
        String date = java.time.LocalDate.now().toString();  // yyyy-MM-dd
        return String.format("files/%s/%d.%s", date, fileId, ext);
    }

    private FileValidator() {}
}
```

**安全设计要点**：

| 威胁 | 防御措施 |
|---|---|
| 路径穿越 `../../../etc/passwd` | `lastIndexOf('.')` + 正则 `[a-zA-Z0-9]{1,16}` 仅保留合法字符 |
| 可执行文件 `virus.exe` | MIME 白名单排除 `application/x-msdownload` 等 |
| 超大文件 DoS | 按 purpose 区分 50MB/100MB 限制 |
| 空扩展名 | 默认 `.bin` 兜底 |
| 大小写绕过 `.JPG` vs `.jpg` | `toLowerCase()` 统一 |

---

## 11. Service 层

### 11.1 `service/FileService.java`（接口）

```java
package lanshan.manmu.file.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.file.*;
import lanshan.manmu.file.model.entity.FileEntity;

public interface FileService {

    GetUploadURLResp getUploadURL(GetUploadURLReq req);

    ConfirmUploadResp confirmUpload(ConfirmUploadReq req);

    GetDownloadURLResp getDownloadURL(GetDownloadURLReq req);

    FileInfo getFileInfo(long fileId, long userId);

    List<FileInfo> batchGetFileInfo(List<Long> fileIds, long userId);

    void deleteFile(long fileId, long userId);

    /**
     * 清理超时未确认的 zombie 记录。
     * 由 @Scheduled 调度调用。
     */
    void cleanupZombieFiles();
}
```

### 11.2 `service/impl/FileServiceImpl.java`（完整实现）

```java
package lanshan.manmu.file.service.impl;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lanshan.manmu.common.constant.CommonConst;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.file.*;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.file.config.MinioConfig;
import lanshan.manmu.file.mapper.FileMapper;
import lanshan.manmu.file.model.entity.FileEntity;
import lanshan.manmu.file.service.FileService;
import lanshan.manmu.file.util.FileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final SnowflakeIdWorker snowflake;

    public FileServiceImpl(
            FileMapper fileMapper,
            MinioClient minioClient,
            MinioConfig minioConfig,
            SnowflakeIdWorker snowflake) {
        this.fileMapper = fileMapper;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.snowflake = snowflake;
    }

    // ==================== getUploadURL ====================

    /**
     * 预分配 fileId + 生成 Presigned PUT URL + 写 DB 占位记录。
     *
     * 异常场景：
     *   - name 为空 → BAD_REQUEST
     *   - size ≤ 0  → BAD_REQUEST
     *   - MIME 不在白名单 → FILE_TYPE_NOT_SUPPORT
     *   - size 超限 → FILE_TOO_LARGE
     *   - MinIO 生成 URL 失败 → FILE_UPLOAD_FAILED（记日志 + 抛异常）
     */
    @Override
    public GetUploadURLResp getUploadURL(GetUploadURLReq req) {
        // 1. 参数校验
        if (req.getName() == null || req.getName().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }

        // 2. 安全校验（大小 + MIME + ext）
        FileValidator.validateSize(req.getSize(), req.getPurpose());
        FileValidator.validateMimeType(req.getMimeType());
        String safeExt = FileValidator.safeExtractExt(req.getName());

        // 3. 预分配 fileId
        long fileId = snowflake.nextId();

        // 4. 生成 objectKey
        String objectKey = FileValidator.buildObjectKey(fileId, safeExt);

        // 5. 生成 Presigned PUT URL（服务端固定 1800 秒，忽略 req.getExpiresIn()）
        String uploadUrl;
        try {
            uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .expiry(CommonConst.FILE_PRESIGN_EXPIRE_SEC, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 生成 Presigned PUT URL 失败, fileId={}, key={}", fileId, objectKey, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "生成上传 URL 失败");
        }

        // 6. 预写 DB 占位记录（status=PENDING）
        FileEntity entity = new FileEntity();
        entity.setId(fileId);
        entity.setName(req.getName());
        entity.setKey(objectKey);
        entity.setSize(req.getSize());
        entity.setMimeType(req.getMimeType());
        entity.setExt(safeExt);
        entity.setWidth(0);
        entity.setHeight(0);
        entity.setDuration(0);
        entity.setMd5("");
        entity.setPurpose(req.getPurpose());
        entity.setAccess(req.getAccess());
        entity.setUploaderId(req.getUploaderId());
        entity.setBucket(minioConfig.getBucket());
        entity.setStatus(CommonConst.FILE_STATUS_PENDING);
        entity.setCreatedAt(OffsetDateTime.now());
        fileMapper.insert(entity);

        // 7. 返回
        long expiresAt = System.currentTimeMillis()
                + CommonConst.FILE_PRESIGN_EXPIRE_SEC * 1000L;
        GetUploadURLResp resp = new GetUploadURLResp();
        resp.setFileId(fileId);
        resp.setUploadUrl(uploadUrl);
        resp.setKey(objectKey);
        resp.setExpiresAt(expiresAt);
        return resp;
    }

    // ==================== confirmUpload ====================

    /**
     * 客户端上传完成后回调确认。
     * 将 status 从 PENDING → CONFIRMED，可选记录 MD5。
     *
     * 异常场景：
     *   - fileId 不存在 → FILE_NOT_FOUND
     *   - status 不是 PENDING → BAD_REQUEST（已确认或已删除）
     *   - uploaderId 不匹配 → FILE_NOT_UPLOADER
     *   - MD5 不匹配（Phase 1 仅日志不阻断）
     */
    @Override
    @Transactional
    public ConfirmUploadResp confirmUpload(ConfirmUploadReq req) {
        FileEntity entity = fileMapper.selectById(req.getFileId());
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }

        // 校验上传者
        if (entity.getUploaderId() != req.getUploaderId()) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADER);
        }

        // 校验状态
        if (entity.getStatus() != CommonConst.FILE_STATUS_PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "文件状态非 PENDING，当前状态: " + entity.getStatus());
        }

        // MD5 处理（Phase 1 仅日志不阻断）
        if (req.getMd5() != null && !req.getMd5().isEmpty()) {
            if (entity.getMd5() != null && !entity.getMd5().isEmpty()
                    && !entity.getMd5().equals(req.getMd5())) {
                // MD5 不一致：Phase 1 仅记录日志，不阻断
                log.warn("MD5 不一致, fileId={}, dbMd5={}, reqMd5={}",
                        req.getFileId(), entity.getMd5(), req.getMd5());
            }
            // 更新 MD5
            entity.setMd5(req.getMd5());
        }

        // 更新状态为 CONFIRMED
        entity.setStatus(CommonConst.FILE_STATUS_CONFIRMED);
        fileMapper.updateById(entity);

        log.info("文件确认上传成功, fileId={}, key={}", req.getFileId(), entity.getKey());

        ConfirmUploadResp resp = new ConfirmUploadResp();
        resp.setFile(toFileInfo(entity));
        return resp;
    }

    // ==================== getDownloadURL ====================

    /**
     * 生成 Presigned GET URL。
     *
     * 访问控制（Phase 1）：
     *   - 校验文件存在 + status=CONFIRMED
     *   - 不校验 uploader 关系（IM 场景：群成员可下载他人发的文件）
     *   - Phase 2 接入 ConvRpcService.isMember() 做会话级权限
     *
     * 异常场景：
     *   - fileId 不存在 → FILE_NOT_FOUND
     *   - status=PENDING → FILE_PENDING
     *   - status=DELETED → FILE_DELETED
     *   - MinIO 生成 URL 失败 → INTERNAL_ERROR（记日志 + 抛异常）
     */
    @Override
    public GetDownloadURLResp getDownloadURL(GetDownloadURLReq req) {
        FileEntity entity = fileMapper.selectById(req.getFileId());
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }

        // 状态校验
        checkFileAvailable(entity);

        // 生成 Presigned GET URL（服务端固定 1800 秒）
        String downloadUrl;
        try {
            downloadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(entity.getBucket())
                            .object(entity.getKey())
                            .expiry(CommonConst.FILE_PRESIGN_EXPIRE_SEC, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 生成 Presigned GET URL 失败, fileId={}, key={}",
                    req.getFileId(), entity.getKey(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成下载 URL 失败");
        }

        long expiresAt = System.currentTimeMillis()
                + CommonConst.FILE_PRESIGN_EXPIRE_SEC * 1000L;
        GetDownloadURLResp resp = new GetDownloadURLResp();
        resp.setDownloadUrl(downloadUrl);
        resp.setExpiresAt(expiresAt);
        resp.setFile(toFileInfo(entity));
        return resp;
    }

    // ==================== getFileInfo ====================

    /**
     * 查询单个文件信息。
     * Phase 1 访问控制：只校验文件存在 + CONFIRMED，不校验请求者身份。
     */
    @Override
    public FileInfo getFileInfo(long fileId, long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        checkFileAvailable(entity);
        return toFileInfo(entity);
    }

    // ==================== batchGetFileInfo ====================

    /**
     * 批量查询文件信息。
     * 只返回 status=CONFIRMED 的文件，跳过 PENDING/DELETED。
     */
    @Override
    public List<FileInfo> batchGetFileInfo(List<Long> fileIds, long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<FileEntity> entities = fileMapper.selectBatchIds(fileIds);
        return entities.stream()
                .filter(e -> e.getStatus() == CommonConst.FILE_STATUS_CONFIRMED)
                .map(this::toFileInfo)
                .collect(Collectors.toList());
    }

    // ==================== deleteFile ====================

    /**
     * 删除文件（软删除 DB + best-effort 删除 MinIO 对象）。
     *
     * 访问控制：仅 uploader 可删。
     *
     * 异常场景：
     *   - fileId 不存在 → FILE_NOT_FOUND
     *   - userId ≠ uploaderId → FILE_NOT_UPLOADER
     *   - status=DELETED → FILE_DELETED（幂等，重复删除返回错误）
     *   - MinIO 删除失败 → 仅日志不阻断（DB 已软删，MinIO 对象可后续清理）
     */
    @Override
    @Transactional
    public void deleteFile(long fileId, long userId) {
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }

        // 归属校验
        if (entity.getUploaderId() != userId) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADER);
        }

        // 幂等校验
        if (entity.getStatus() == CommonConst.FILE_STATUS_DELETED) {
            throw new BizException(ErrorCode.FILE_DELETED);
        }

        // 软删除 DB
        entity.setStatus(CommonConst.FILE_STATUS_DELETED);
        fileMapper.updateById(entity);

        // best-effort 删除 MinIO 对象（失败仅日志，不回滚事务）
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(entity.getBucket())
                            .object(entity.getKey())
                            .build());
            log.info("MinIO 对象删除成功, key={}", entity.getKey());
        } catch (Exception e) {
            log.warn("MinIO 对象删除失败（不影响 DB 软删）, key={}", entity.getKey(), e);
        }

        log.info("文件删除成功, fileId={}, key={}", fileId, entity.getKey());
    }

    // ==================== cleanupZombieFiles ====================

    /**
     * 定时清理 zombie 记录：status=PENDING 且 created_at 超过 30 分钟。
     * 同时 best-effort 删除对应的 MinIO 对象。
     *
     * 由 @Scheduled 调度，每 5 分钟执行一次。
     */
    @Override
    @Transactional
    public void cleanupZombieFiles() {
        OffsetDateTime threshold = OffsetDateTime.now()
                .minus(CommonConst.FILE_ZOMBIE_TTL_MINUTES, ChronoUnit.MINUTES);

        // 查询 zombie 记录
        List<FileEntity> zombies = fileMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEntity>()
                        .eq(FileEntity::getStatus, CommonConst.FILE_STATUS_PENDING)
                        .lt(FileEntity::getCreatedAt, threshold));

        if (zombies.isEmpty()) {
            return;
        }

        log.info("发现 {} 条 zombie 文件记录，开始清理", zombies.size());

        for (FileEntity zombie : zombies) {
            // best-effort 删除 MinIO 对象（客户端可能已上传但未确认）
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(zombie.getBucket())
                                .object(zombie.getKey())
                                .build());
            } catch (Exception e) {
                log.warn("zombie MinIO 对象删除失败, key={}", zombie.getKey(), e);
                // 不跳过 DB 删除——即使 MinIO 删失败，DB 记录也要清
            }

            // 硬删除 DB 记录（zombie 直接物理删除，不留软删痕迹）
            fileMapper.deleteById(zombie.getId());
        }

        log.info("zombie 清理完成，共清理 {} 条", zombies.size());
    }

    // ==================== 内部工具方法 ====================

    /**
     * 校验文件状态是否可用（CONFIRMED）。
     * PENDING → FILE_PENDING，DELETED → FILE_DELETED。
     */
    private void checkFileAvailable(FileEntity entity) {
        if (entity.getStatus() == CommonConst.FILE_STATUS_PENDING) {
            throw new BizException(ErrorCode.FILE_PENDING);
        }
        if (entity.getStatus() == CommonConst.FILE_STATUS_DELETED) {
            throw new BizException(ErrorCode.FILE_DELETED);
        }
    }

    /**
     * FileEntity → FileInfo DTO 转换。
     */
    private FileInfo toFileInfo(FileEntity entity) {
        if (entity == null) return null;
        FileInfo info = new FileInfo();
        info.setFileId(entity.getId());
        info.setName(entity.getName());
        info.setKey(entity.getKey());
        info.setSize(entity.getSize());
        info.setMimeType(entity.getMimeType());
        info.setExt(entity.getExt());
        info.setWidth(entity.getWidth() != null ? entity.getWidth() : 0);
        info.setHeight(entity.getHeight() != null ? entity.getHeight() : 0);
        info.setDuration(entity.getDuration() != null ? entity.getDuration() : 0);
        info.setMd5(entity.getMd5());
        info.setPurpose(entity.getPurpose() != null ? entity.getPurpose() : 0);
        info.setAccess(entity.getAccess() != null ? entity.getAccess() : 0);
        info.setUploaderId(entity.getUploaderId());
        info.setBucket(entity.getBucket());
        info.setStatus(entity.getStatus() != null ? entity.getStatus() : 0);
        info.setCreatedAt(toEpochMillis(entity.getCreatedAt()));
        return info;
    }

    /**
     * OffsetDateTime → epoch millis。
     */
    private long toEpochMillis(OffsetDateTime odt) {
        return odt != null ? odt.toInstant().toEpochMilli() : 0L;
    }
}
```

---

## 12. Dubbo RPC 实现

### `rpc/FileRpcServiceImpl.java`

```java
package lanshan.manmu.file.rpc;

import java.util.List;
import lanshan.manmu.common.rpc.FileRpcService;
import lanshan.manmu.common.rpc.dto.file.*;
import lanshan.manmu.file.service.FileService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 文件服务 Dubbo Provider 实现。
 * 薄转发层：只做参数映射，业务逻辑在 FileService 层。
 */
@DubboService
public class FileRpcServiceImpl implements FileRpcService {

    private final FileService fileService;

    public FileRpcServiceImpl(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public GetUploadURLResp getUploadURL(GetUploadURLReq req) {
        return fileService.getUploadURL(req);
    }

    @Override
    public ConfirmUploadResp confirmUpload(ConfirmUploadReq req) {
        return fileService.confirmUpload(req);
    }

    @Override
    public GetDownloadURLResp getDownloadURL(GetDownloadURLReq req) {
        return fileService.getDownloadURL(req);
    }

    @Override
    public GetFileInfoResp getFileInfo(GetFileInfoReq req) {
        GetFileInfoResp resp = new GetFileInfoResp();
        resp.setFile(fileService.getFileInfo(req.getFileId(), req.getUserId()));
        return resp;
    }

    @Override
    public BatchGetFileInfoResp batchGetFileInfo(BatchGetFileInfoReq req) {
        BatchGetFileInfoResp resp = new BatchGetFileInfoResp();
        resp.setFiles(fileService.batchGetFileInfo(req.getFileIds(), req.getUserId()));
        return resp;
    }

    @Override
    public void deleteFile(DeleteFileReq req) {
        fileService.deleteFile(req.getFileId(), req.getUserId());
    }
}
```

---

## 13. Zombie 清理调度器

### `scheduler/ZombieFileCleaner.java`

```java
package lanshan.manmu.file.scheduler;

import lanshan.manmu.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Zombie 文件定时清理。
 * <p>清理条件：status=PENDING 且 created_at 超过 30 分钟。
 * <p>调度频率：每 5 分钟扫一次。
 */
@Slf4j
@Component
public class ZombieFileCleaner {

    private final FileService fileService;

    public ZombieFileCleaner(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(fixedDelay = 300_000)  // 5 分钟 = 300_000 ms
    public void cleanup() {
        try {
            fileService.cleanupZombieFiles();
        } catch (Exception e) {
            log.error("zombie 文件清理任务异常", e);
        }
    }
}
```

### 启动类追加 `@EnableScheduling`

```java
@SpringBootApplication
@EnableDubbo
@EnableScheduling                    // 新增：激活定时任务
@MapperScan("lanshan.manmu.file.mapper")
public class FileServiceApplication { ... }
```

> **设计要点**：`@Scheduled(fixedDelay)` 而非 `fixedRate`——`fixedDelay` 是上次执行结束后等 5 分钟再执行，避免任务堆积；`fixedRate` 是固定频率，如果清理耗时超过间隔会叠加执行。

---

## 14. 字段追踪表

### 14.1 DTO 字段全链路追踪

#### GetUploadURLReq

| 字段 | 来源 | 是否使用 | 备注 |
|---|---|---|---|
| name | 客户端 | ✅ 提取 ext + 写 DB | 经 FileValidator.safeExtractExt 安全过滤 |
| mimeType | 客户端 | ✅ 白名单校验 + 写 DB | 经 FileValidator.validateMimeType 校验 |
| size | 客户端 | ✅ 大小校验 + 写 DB | 经 FileValidator.validateSize 校验 |
| uploaderId | 客户端 | ✅ 写 DB | 后续接入 UserContext 后从 attachment 获取 |
| purpose | 客户端 | ✅ 大小限制判断 + 写 DB | 1=附件 2=头像 |
| access | 客户端 | ✅ 写 DB | Phase 1 不用于下载校验 |
| expiresIn | 客户端 | ❌ **忽略** | 服务端固定 1800 秒（决策 #11） |

#### GetUploadURLResp

| 字段 | 写入方 | 读取方 | 备注 |
|---|---|---|---|
| fileId | FileServiceImpl | 客户端 | Snowflake 预分配 |
| uploadUrl | FileServiceImpl | 客户端 | MinIO Presigned PUT URL |
| key | FileServiceImpl | 客户端 | object key，客户端上传时不需要但调试有用 |
| expiresAt | FileServiceImpl | 客户端 | epoch millis |

#### ConfirmUploadReq

| 字段 | 来源 | 是否使用 | 备注 |
|---|---|---|---|
| fileId | 客户端 | ✅ 查 DB | |
| uploaderId | 客户端 | ✅ 归属校验 | |
| md5 | 客户端 | ✅ 仅日志 | Phase 1 不阻断（决策 #13） |

#### GetDownloadURLReq

| 字段 | 来源 | 是否使用 | 备注 |
|---|---|---|---|
| fileId | 客户端 | ✅ 查 DB | |
| userId | 客户端 | ✅ 预留 | Phase 1 不做会话级校验，Phase 2 接入 ConvRpcService |
| expiresIn | 客户端 | ❌ **忽略** | 服务端固定 1800 秒（决策 #11） |

#### FileInfo

| 字段 | 写入方 | 读取方 | 备注 |
|---|---|---|---|
| fileId | toFileInfo | 客户端 | |
| name | toFileInfo | 客户端 | 原始文件名 |
| key | toFileInfo | 客户端 | MinIO object key |
| size | toFileInfo | 客户端 | 字节 |
| mimeType | toFileInfo | 客户端 | |
| ext | toFileInfo | 客户端 | 安全过滤后的小写扩展名 |
| width | toFileInfo | 客户端 | **Phase 1 始终 0**（决策 #3） |
| height | toFileInfo | 客户端 | **Phase 1 始终 0** |
| duration | toFileInfo | 客户端 | **Phase 1 始终 0** |
| md5 | toFileInfo | 客户端 | confirmUpload 后可能有值 |
| purpose | toFileInfo | 客户端 | |
| access | toFileInfo | 客户端 | |
| uploaderId | toFileInfo | 客户端 | |
| bucket | toFileInfo | 客户端 | 默认 "aim" |
| status | toFileInfo | 客户端 | **新增字段** |
| createdAt | toFileInfo | 客户端 | epoch millis |

### 14.2 无死字段确认

| 检查项 | 结果 |
|---|---|
| FileInfo 所有字段都有 toFileInfo 写入 | ✅ |
| FileInfo 所有字段都有客户端读取场景 | ✅ |
| Entity 所有字段都有写入路径 | ✅（width/height/duration 由 DB DEFAULT 0 填充） |
| 无不被引用的 Config Bean | ✅（无 RedisConfig、无 RedisTemplate） |

---

## 15. 错误处理矩阵

| 方法 | 异常场景 | ErrorCode | 日志级别 | 是否抛出 |
|---|---|---|---|---|
| getUploadURL | name 为空 | BAD_REQUEST | 不记日志 | ✅ |
| getUploadURL | size ≤ 0 | BAD_REQUEST | 不记日志 | ✅ |
| getUploadURL | MIME 不在白名单 | FILE_TYPE_NOT_SUPPORT | 不记日志 | ✅ |
| getUploadURL | size 超限 | FILE_TOO_LARGE | 不记日志 | ✅ |
| getUploadURL | MinIO 生成 URL 失败 | FILE_UPLOAD_FAILED | ERROR + 堆栈 | ✅ |
| confirmUpload | fileId 不存在 | FILE_NOT_FOUND | 不记日志 | ✅ |
| confirmUpload | uploaderId 不匹配 | FILE_NOT_UPLOADER | 不记日志 | ✅ |
| confirmUpload | status 非 PENDING | BAD_REQUEST | 不记日志 | ✅ |
| confirmUpload | MD5 不一致 | 不抛出 | WARN | ❌（Phase 1 仅日志） |
| confirmUpload | 成功 | — | INFO | — |
| getDownloadURL | fileId 不存在 | FILE_NOT_FOUND | 不记日志 | ✅ |
| getDownloadURL | status=PENDING | FILE_PENDING | 不记日志 | ✅ |
| getDownloadURL | status=DELETED | FILE_DELETED | 不记日志 | ✅ |
| getDownloadURL | MinIO 生成 URL 失败 | INTERNAL_ERROR | ERROR + 堆栈 | ✅ |
| deleteFile | fileId 不存在 | FILE_NOT_FOUND | 不记日志 | ✅ |
| deleteFile | userId ≠ uploaderId | FILE_NOT_UPLOADER | 不记日志 | ✅ |
| deleteFile | status=DELETED | FILE_DELETED | 不记日志 | ✅ |
| deleteFile | MinIO 删除失败 | 不抛出 | WARN | ❌（best-effort） |
| deleteFile | 成功 | — | INFO | — |
| cleanupZombie | MinIO 删除失败 | 不抛出 | WARN | ❌（best-effort） |
| cleanupZombie | 整体异常 | 不抛出 | ERROR + 堆栈 | ❌（调度器 catch） |

> **设计原则**：参数校验不记日志（高频，噪音大）；MinIO 操作失败记 ERROR/WARN + 完整堆栈；业务操作成功记 INFO。

---

## 16. 事务边界标注

| 方法 | @Transactional | 理由 |
|---|---|---|
| getUploadURL | ❌ 不加 | 单次 insert，无多步写操作 |
| confirmUpload | ✅ 加 | 更新 status + 更新 md5（虽然同一行 updateById，但保持一致性） |
| getDownloadURL | ❌ 不加 | 纯读操作 |
| getFileInfo | ❌ 不加 | 纯读操作 |
| batchGetFileInfo | ❌ 不加 | 纯读操作 |
| deleteFile | ✅ 加 | 更新 DB status + 删除 MinIO（MinIO 失败不回滚，但 DB 操作需要事务保护） |
| cleanupZombieFiles | ✅ 加 | 循环 deleteById，整体事务保证一致性 |

---

## 17. 文件清单汇总

### common 模块改动（3 个文件）

| 操作 | 文件 | 说明 |
|---|---|---|
| 修改 | `exception/ErrorCode.java` | 追加 FILE_PENDING/FILE_DELETED/FILE_NOT_UPLOADER/FILE_NAME_INVALID |
| 修改 | `constant/CommonConst.java` | 追加文件状态/大小限制/Presigned 过期/zombie 常量（保留原 MAX_FILE_SIZE 不动） |
| 修改 | `rpc/dto/file/FileInfo.java` | 追加 status 字段 |

### file-service 新增文件（8 个 Java 类 + 1 个 yml 修改 + 1 个 pom 修改）

```
file-service/src/main/java/lanshan/manmu/file/
├── FileServiceApplication.java        # 修改: 加 @EnableDubbo + @EnableScheduling + @MapperScan
├── config/
│   ├── MybatisPlusConfig.java         # 分页插件（无 @MapperScan）
│   ├── MinioConfig.java               # @ConfigurationProperties + bucket 自动创建
│   └── SnowflakeConfig.java           # workerId=4
├── model/entity/
│   └── FileEntity.java                # @TableName("files"), 15 字段, OffsetDateTime
├── mapper/
│   └── FileMapper.java                # extends BaseMapper<FileEntity>
├── util/
│   └── FileValidator.java             # ext 安全过滤 + MIME 白名单 + 大小校验
├── service/
│   ├── FileService.java               # 接口: 7 个方法
│   └── impl/
│       └── FileServiceImpl.java       # @Slf4j @Service, 完整实现
├── rpc/
│   └── FileRpcServiceImpl.java        # @DubboService, 薄转发
└── scheduler/
    └── ZombieFileCleaner.java         # @Scheduled(fixedDelay=300_000)
```

### SQL 变更（1 条）

```sql
ALTER TABLE file.files ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_files_status_created
    ON file.files(status, created_at) WHERE status = 0;
```

> 需手动执行或追加到 `aim-schema.sql` 末尾。

---

## 18. 验证清单

### 18.1 编译验证

```bash
# 1. 先执行 SQL 变更
psql -h localhost -U postgres -d aim -c "ALTER TABLE file.files ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 0;"
psql -h localhost -U postgres -d aim -c "CREATE INDEX IF NOT EXISTS idx_files_status_created ON file.files(status, created_at) WHERE status = 0;"

# 2. 编译
mvn compile -pl common -pl file-service
```

### 18.2 功能验证

```bash
# 3. 启动基础设施
docker compose up -d

# 4. 启动 file-service
# 日志应显示 "MinIO bucket 'aim' 创建成功" 或 "bucket 已存在"
# Dubbo 注册 Nacos 成功

# 5. Dubbo 直连测试（用测试类或 telnet 20886）
```

| 测试场景 | 操作 | 预期结果 |
|---|---|---|
| 正常上传 | getUploadURL("test.jpg", "image/jpeg", 102400, 1, 1, 1) | 返回 fileId + uploadUrl |
| curl 上传 | `curl -X PUT -T ./test.jpg "{uploadUrl}"` | HTTP 200 |
| 确认上传 | confirmUpload(fileId, uploaderId, null) | 返回 FileInfo, status=1 |
| 下载 | getDownloadURL(fileId, userId, 0) | 返回 downloadUrl |
| curl 下载 | `curl "{downloadUrl}" -o downloaded.jpg` | 文件内容一致 |
| 查信息 | getFileInfo(fileId, userId) | 返回 FileInfo |
| 批量查 | batchGetFileInfo([fileId1, fileId2], userId) | 返回 List<FileInfo> |
| 删除 | deleteFile(fileId, uploaderId) | 成功 |
| 删除后查 | getFileInfo(fileId, userId) | FILE_DELETED |
| 非上传者删 | deleteFile(fileId, otherUserId) | FILE_NOT_UPLOADER |
| 未确认下载 | getDownloadURL(pendingFileId, userId) | FILE_PENDING |

### 18.3 安全验证

| 测试场景 | 操作 | 预期结果 |
|---|---|---|
| 路径穿越 | getUploadURL("../../../etc/passwd", "text/plain", 100, 1, 1, 1) | ext="bin", key 不含 `..` |
| 可执行文件 | getUploadURL("virus.exe", "application/x-msdownload", 100, 1, 1, 1) | FILE_TYPE_NOT_SUPPORT |
| 超大图片 | getUploadURL("big.jpg", "image/jpeg", 60*1024*1024, 1, 2, 1) | FILE_TOO_LARGE |
| 超大附件 | getUploadURL("big.zip", "application/zip", 200*1024*1024, 1, 1, 1) | FILE_TOO_LARGE |
| expiresIn 忽略 | getUploadURL(..., expiresIn=10) | 返回的 expiresAt = now + 1800s |

### 18.4 Zombie 清理验证

| 测试场景 | 操作 | 预期结果 |
|---|---|---|
| 生成 zombie | getUploadURL 但不调 confirmUpload | DB 有 status=0 的记录 |
| 等待清理 | 等待 30 分钟（或手动改 created_at） | 5 分钟内被清理 |
| 清理后 MinIO | 检查 MinIO | 对象已删除 |
| 清理后 DB | selectById(fileId) | null（物理删除） |

---

## 19. 学习要点对照

| 技术点 | 在哪学 | 要点 |
|---|---|---|
| MinIO Presigned URL | FileServiceImpl.getUploadURL | 两阶段上传：预分配 → 直传 → 确认 |
| 路径穿越防御 | FileValidator.safeExtractExt | 客户端输入不可信，ext 正则过滤 |
| MIME 白名单 | FileValidator.validateMimeType | 前缀匹配 + 精确匹配双层校验 |
| 文件状态机 | FileEntity.status | PENDING → CONFIRMED / DELETED |
| Zombie 清理 | ZombieFileCleaner | @Scheduled + best-effort MinIO 删除 |
| @Transactional 边界 | FileServiceImpl | 写操作加事务，读操作不加 |
| best-effort 模式 | deleteFile / cleanupZombie | MinIO 删失败不阻断 DB 操作 |
| OffsetDateTime | FileEntity.createdAt | 与 PG TIMESTAMPTZ 对齐，无时区丢失 |
| @Slf4j 日志规范 | FileServiceImpl | 参数校验不记日志，IO 失败记 ERROR |
| @ConfigurationProperties | MinioConfig | 类型安全的配置绑定 |
| fail-fast 初始化 | MinioConfig.minioClient | bucket 初始化失败阻止启动 |