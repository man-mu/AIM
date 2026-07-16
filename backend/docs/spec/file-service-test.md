```markdown /Users/manmu/code/IDEA_project/AIM/backend/docs/spec/file-service-test.md
# file-service 集成测试 spec

> **目标**：为 file-service 编写 JUnit 5 集成测试，覆盖核心业务流程 + 安全校验 + 异常场景。
> **测试策略**：`@SpringBootTest` 构造器注入 `FileService`，本地调用不走 Dubbo 网络层；专用 `TestConfig` 排除 Dubbo 自动配置与 `rpc` 包扫描，避免 Dubbo 启动 / 端口占用 / Nacos 连接。
> **前置条件**：PostgreSQL + MinIO 必须运行（Nacos 因 Dubbo 隔离已不再依赖）。

---

## 0. 设计决策

| # | 决策项 | 选择 | 理由 |
|---|---|---|---|
| 1 | 测试框架 | JUnit 5 + `@SpringBootTest` | JUnit 5 由 `spring-boot-starter-test` 携带，Spring Boot 标准方案 |
| 2 | 调用方式 | 构造器注入 `FileService` + `@Autowired`，不走 Dubbo | 守 AGENTS.md 反字段注入规范（禁止字段注入）；行 JUnit5 默认 FIELD 模式需 `@Autowired` 显式开启构造器注入 |
| 3 | Spring Context | 专用 `TestConfig` 隔离 `FileServiceApplication` | 避开 `@EnableDubbo` 完全不启动 Dubbo Provider，测试不连 Nacos / 不占 20886 端口 |
| 4 | Dubbo 隔离 | `@EnableAutoConfiguration(exclude = {DubboAutoConfiguration, DubboListener...})` + `@ComponentScan(excludeFilters = rpc..)` | 既排除 Dubbo 自动配置，又不扫描 `FileRpcServiceImpl`（带 `@DubboService`），让测试纯净 |
| 5 | 测试实例 | `@TestInstance(Lifecycle.PER_CLASS)` | 单实例复用，允许 `@AfterAll` 非静态访问实例 `fileMapper` 做清理 |
| 6 | 测试顺序 | `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` | 主链路用例依赖前面产生的 fileId |
| 7 | 数据清理 | `@AfterAll` 硬删 `deleteBatchIds` 所有 `createdFileIds` | 测试不只产生软删行，PENDING 行会污染表且被 zombie 清理器误删；统一物理删除幂等可重复执行 |
| 8 | Mock 策略 | 不 Mock，全部真实环境 | 集成测试的意义就是验证真实交互 |

---

## 1. 依赖添加

### 1.1 file-service pom.xml 追加

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

> `spring-boot-starter-test` 已包含 JUnit 5 + Spring Boot Test + AssertJ + Mockito，无需逐个引入。

### 1.2 file-service/src/test/resources/application.yml（必需）

**为何要放测试 application.yml**：Dubbo 的 `DubboApplicationContextInitializer` 在测试启动时若发现 `target/test-classes/application.yml` 不存在，会自动写入一个最小 stub：

```yaml
dubbo:
  config-center:
    address: "N/A"
```

Spring Boot classpath 中 `target/test-classes/` 优先于 `target/classes/`，这个 stub 会**覆盖** main 的 `application.yml`，导致 `spring.datasource.*` 全部失配（"Failed to determine a suitable driver class"）。

**解决方案**：在 `file-service/src/test/resources/application.yml` 显式放置一份轻量配置，maven 在 `process-test-resources` 阶段拷贝到 `target/test-classes/`，Dubbo 检测到文件已存在就不再生成 stub，且 Spring 正常加载我们的配置。

完整内容：

```yaml
spring:
  application:
    name: file-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/aim?currentSchema=file
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  # 测试不依赖 Nacos discovery（Dubbo 自动配置已 exclude，无需 cloud.nacos）
  cloud:
    nacos:
      discovery:
        enabled: false

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

> **不要**在此 yml 写 `dubbo.*` 任何配置——Dubbo 自动配置已被 `TestConfig.exclude` 排除，写了反而可能触发 Dubbo 启动；让 Dubbo 完全从测试上下文消失才是关键。

---

## 2. 测试文件

### 文件路径

```
file-service/src/test/java/lanshan/manmu/file/service/FileServiceImplTest.java
```

### 完整代码

```java
package lanshan.manmu.file.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import lanshan.manmu.common.constant.CommonConst;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.file.*;
import lanshan.manmu.file.mapper.FileMapper;
import lanshan.manmu.file.model.entity.FileEntity;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * file-service 集成测试。
 * <p>构造器注入 {@link FileService} 本地调用，不走 Dubbo 网络层。
 * <p>使用专用 {@link TestConfig} 替代 {@code FileServiceApplication}，避免 {@code @EnableDubbo}
 * 触发 Dubbo Provider 真实启动；同时 {@code exclude} 排除 Dubbo 全部自动配置，
 * 避免测试时连 Nacos config-center / metadata-report / 启动 Dubbo 协议端口。
 * 本测试只验证业务逻辑（FileService / Mapper / MinIO），不需要 Dubbo 暴露。
 * <p>{@code @TestInstance(PER_CLASS)} 使 {@link AfterAll} 非静态，复用实例 {@code fileMapper} 做硬删清理。
 * <p>依赖：PostgreSQL + MinIO 必须运行（已无 Nacos / Dubbo 依赖）。
 */
@SpringBootTest(classes = FileServiceImplTest.TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileServiceImplTest {

    /**
     * 测试专用 Spring Boot 配置。
     * <p>不继承 {@link lanshan.manmu.file.FileServiceApplication} 以跳过 {@code @EnableDubbo}；
     * 通过 {@code exclude} 显式排除 Dubbo 自动配置，避免触发 Nacos 连接与端口占用；
     * {@code excludeFilters} 跳过 {@code rpc/} 包扫描（{@code @DubboService} 注解会触发 Dubbo 启动）。
     * 仅保留自动配置 + Mapper 扫描 + 业务 Bean 扫描（service/config/util/scheduler/mapper）。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration.class,
            org.apache.dubbo.spring.boot.autoconfigure.DubboListenerAutoConfiguration.class,
            org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration.class,
            org.apache.dubbo.spring.boot.autoconfigure.DubboTripleAutoConfiguration.class
    })
    @MapperScan("lanshan.manmu.file.mapper")
    @ComponentScan(
            basePackages = "lanshan.manmu.file",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "lanshan\\.manmu\\.file\\.rpc\\..*"
            )
    )
    static class TestConfig {
    }

    private static final long UPLOADER_ID   = 99999L;
    private static final long OTHER_USER_ID = 88888L;
    private static final long NOT_EXIST_ID  = 999_999L;

    private final FileService fileService;
    private final FileMapper fileMapper;

    /** 主链路 fileId（getUploadURL→confirm→download→delete） */
    private long fileId;
    /** 未确认文件 fileId（getDownloadURL PENDING 场景） */
    private long pendingFileId;
    /** 所有经 getUploadURL 创建的 fileId，用于 @AfterAll 硬删 */
    private final List<Long> createdFileIds = new ArrayList<>();

    @Autowired
    public FileServiceImplTest(FileService fileService, FileMapper fileMapper) {
        this.fileService = fileService;
        this.fileMapper = fileMapper;
    }

    @AfterAll
    void cleanup() {
        if (!createdFileIds.isEmpty()) {
            // 物理删除所有测试创建的 DB 行（含 DELETED 软删行 + PENDING 未确认行）
            fileMapper.deleteBatchIds(createdFileIds);
        }
    }

    /** 把 getUploadURL 产生的 fileId 记入清理清单 */
    private long track(long id) {
        createdFileIds.add(id);
        return id;
    }

    // ==================== 主链路：getUploadURL → confirm → download → delete ====================

    /**
     * 正常获取上传 URL。
     * 验证：fileId > 0、uploadUrl 含 "aim"、key 以 "files/" 开头、expiresAt 在未来、DB 有 PENDING 记录。
     * 同时验证 expiresIn 被忽略：req 传 10 秒，resp.expiresAt 仍 > now+60s（说明服务端用了 1800s）。
     */
    @Test
    @Order(1)
    void shouldGetUploadURL() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("test.jpg");
        req.setSize(1024);
        req.setMimeType("image/jpeg");
        req.setPurpose(1);
        req.setAccess(1);
        req.setUploaderId(UPLOADER_ID);
        req.setExpiresIn(10);  // 故意传 10 秒，应被忽略

        GetUploadURLResp resp = fileService.getUploadURL(req);

        assertNotNull(resp);
        assertTrue(resp.getFileId() > 0);
        assertNotNull(resp.getUploadUrl());
        assertTrue(resp.getUploadUrl().contains("aim"));
        assertNotNull(resp.getKey());
        assertTrue(resp.getKey().startsWith("files/"));
        assertTrue(resp.getExpiresAt() > System.currentTimeMillis() + 60_000,
                "expiresAt 至少在 60s 以后，说明服务端忽略 10s 用了 1800s");

        fileId = track(resp.getFileId());

        // 验证 DB 已有 PENDING 记录
        FileEntity entity = fileMapper.selectById(fileId);
        assertNotNull(entity);
        assertEquals(CommonConst.FILE_STATUS_PENDING, entity.getStatus());
    }

    /**
     * 非上传者确认 → FILE_NOT_UPLOADER。
     * 注：confirmUpload 中 uploader 校验先于 status 校验，故 PENDING 状态即可触发。
     */
    @Test
    @Order(2)
    void shouldRejectConfirmByNonUploader() {
        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(fileId);
        req.setUploaderId(OTHER_USER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.confirmUpload(req));
        assertEquals(ErrorCode.FILE_NOT_UPLOADER.getCode(), ex.getCode());
    }

    /**
     * 正常确认上传 → status 变为 CONFIRMED。
     */
    @Test
    @Order(3)
    void shouldConfirmUpload() {
        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(fileId);
        req.setUploaderId(UPLOADER_ID);

        ConfirmUploadResp resp = fileService.confirmUpload(req);

        assertNotNull(resp);
        assertNotNull(resp.getFile());
        assertEquals(fileId, resp.getFile().getFileId());
        assertEquals(CommonConst.FILE_STATUS_CONFIRMED, resp.getFile().getStatus());
    }

    /**
     * 重复确认（已 CONFIRMED）→ BAD_REQUEST。
     */
    @Test
    @Order(4)
    void shouldRejectConfirmTwice() {
        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(fileId);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.confirmUpload(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 正常获取下载 URL（非上传者也能下载，Phase 1 设计）。
     */
    @Test
    @Order(5)
    void shouldGetDownloadURL() {
        GetDownloadURLReq req = new GetDownloadURLReq();
        req.setFileId(fileId);
        req.setUserId(OTHER_USER_ID);

        GetDownloadURLResp resp = fileService.getDownloadURL(req);

        assertNotNull(resp);
        assertNotNull(resp.getDownloadUrl());
        assertTrue(resp.getDownloadUrl().contains("aim"));
        assertTrue(resp.getExpiresAt() > System.currentTimeMillis());
    }

    /**
     * 正常查询文件信息。
     */
    @Test
    @Order(6)
    void shouldGetFileInfo() {
        FileInfo info = fileService.getFileInfo(fileId, UPLOADER_ID);

        assertNotNull(info);
        assertEquals(fileId, info.getFileId());
        assertEquals("test.jpg", info.getName());
        assertEquals(CommonConst.FILE_STATUS_CONFIRMED, info.getStatus());
    }

    /**
     * 批量查询：只返回 CONFIRMED 文件，不存在的 fileId 被跳过。
     */
    @Test
    @Order(7)
    void shouldBatchGetFileInfo() {
        List<FileInfo> infos = fileService.batchGetFileInfo(
                List.of(fileId, NOT_EXIST_ID), UPLOADER_ID);

        assertNotNull(infos);
        assertEquals(1, infos.size());
        assertEquals(fileId, infos.get(0).getFileId());
    }

    /**
     * 非上传者删除 → FILE_NOT_UPLOADER。
     */
    @Test
    @Order(8)
    void shouldRejectDeleteByNonUploader() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.deleteFile(fileId, OTHER_USER_ID));
        assertEquals(ErrorCode.FILE_NOT_UPLOADER.getCode(), ex.getCode());
    }

    /**
     * 正常删除 → status 变为 DELETED。
     */
    @Test
    @Order(9)
    void shouldDeleteFile() {
        fileService.deleteFile(fileId, UPLOADER_ID);

        FileEntity entity = fileMapper.selectById(fileId);
        assertEquals(CommonConst.FILE_STATUS_DELETED, entity.getStatus());
    }

    /**
     * 下载已删除文件 → FILE_DELETED。
     */
    @Test
    @Order(10)
    void shouldRejectDownloadAfterDelete() {
        GetDownloadURLReq req = new GetDownloadURLReq();
        req.setFileId(fileId);
        req.setUserId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getDownloadURL(req));
        assertEquals(ErrorCode.FILE_DELETED.getCode(), ex.getCode());
    }

    /**
     * 重复删除（已 DELETED）→ FILE_DELETED（幂等返回错误）。
     */
    @Test
    @Order(11)
    void shouldRejectDeleteTwice() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.deleteFile(fileId, UPLOADER_ID));
        assertEquals(ErrorCode.FILE_DELETED.getCode(), ex.getCode());
    }

    // ==================== 独立安全校验：getUploadURL 入参校验 ====================

    /**
     * 空文件名 → BAD_REQUEST。
     */
    @Test
    @Order(12)
    void shouldRejectEmptyName() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("");
        req.setSize(1024);
        req.setMimeType("image/jpeg");
        req.setPurpose(1);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getUploadURL(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * 超大图片 → FILE_TOO_LARGE（purpose=2 头像 50MB 限制）。
     */
    @Test
    @Order(13)
    void shouldRejectOversizeImage() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("big.jpg");
        req.setSize(60L * 1024 * 1024); // 60MB > 50MB
        req.setMimeType("image/jpeg");
        req.setPurpose(2);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getUploadURL(req));
        assertEquals(ErrorCode.FILE_TOO_LARGE.getCode(), ex.getCode());
    }

    /**
     * 超大附件 → FILE_TOO_LARGE（purpose=1 附件 100MB 限制）。
     */
    @Test
    @Order(14)
    void shouldRejectOversizeAttachment() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("big.zip");
        req.setSize(200L * 1024 * 1024); // 200MB > 100MB
        req.setMimeType("application/zip");
        req.setPurpose(1);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getUploadURL(req));
        assertEquals(ErrorCode.FILE_TOO_LARGE.getCode(), ex.getCode());
    }

    /**
     * 可执行文件 → FILE_TYPE_NOT_SUPPORT。
     */
    @Test
    @Order(15)
    void shouldRejectExeMime() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("virus.exe");
        req.setSize(1024);
        req.setMimeType("application/x-msdownload");
        req.setPurpose(1);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getUploadURL(req));
        assertEquals(ErrorCode.FILE_TYPE_NOT_SUPPORT.getCode(), ex.getCode());
    }

    /**
     * 路径穿越文件名 → key 不含 ".." 和 "/etc/"，ext 被 fallback 为 "bin"。
     * 同时验证 DB 实体 ext 字段（静默过滤，不抛异常）。
     */
    @Test
    @Order(16)
    void shouldPreventPathTraversal() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("../../../etc/passwd");
        req.setSize(1024);
        req.setMimeType("text/plain");
        req.setPurpose(1);
        req.setUploaderId(UPLOADER_ID);

        GetUploadURLResp resp = fileService.getUploadURL(req);
        long id = track(resp.getFileId());

        assertFalse(resp.getKey().contains(".."));
        assertFalse(resp.getKey().contains("/etc/"));
        assertTrue(resp.getKey().endsWith(".bin"));

        FileEntity entity = fileMapper.selectById(id);
        assertNotNull(entity);
        assertEquals("bin", entity.getExt());
    }

    /**
     * 无扩展名文件 → ext 兜底 "bin"。
     * 验证取 DB 实体 ext 字段（非仅看 key 后缀）。
     */
    @Test
    @Order(17)
    void shouldUseBinForNoExtension() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("noext");
        req.setSize(1024);
        req.setMimeType("text/plain");
        req.setPurpose(1);
        req.setUploaderId(UPLOADER_ID);

        GetUploadURLResp resp = fileService.getUploadURL(req);
        long id = track(resp.getFileId());

        assertTrue(resp.getKey().endsWith(".bin"));
        FileEntity entity = fileMapper.selectById(id);
        assertNotNull(entity);
        assertEquals("bin", entity.getExt());
    }

    // ==================== 独立异常：confirm / download / get / batch / delete 找不到 ID ====================

    /**
     * confirmUpload 不存在 fileId → FILE_NOT_FOUND。
     */
    @Test
    @Order(18)
    void shouldRejectConfirmNotFound() {
        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(NOT_EXIST_ID);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.confirmUpload(req));
        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * 下载未确认文件 → FILE_PENDING。新建一个 PENDING 文件，不 confirm 直接 download。
     */
    @Test
    @Order(19)
    void shouldRejectDownloadPendingFile() {
        GetUploadURLReq upReq = new GetUploadURLReq();
        upReq.setName("pending.png");
        upReq.setSize(1024);
        upReq.setMimeType("image/png");
        upReq.setPurpose(1);
        upReq.setUploaderId(UPLOADER_ID);
        pendingFileId = track(fileService.getUploadURL(upReq).getFileId());

        GetDownloadURLReq req = new GetDownloadURLReq();
        req.setFileId(pendingFileId);
        req.setUserId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getDownloadURL(req));
        assertEquals(ErrorCode.FILE_PENDING.getCode(), ex.getCode());
    }

    /**
     * getFileInfo 不存在 fileId → FILE_NOT_FOUND。
     */
    @Test
    @Order(20)
    void shouldRejectGetFileInfoNotFound() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.getFileInfo(NOT_EXIST_ID, UPLOADER_ID));
        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * deleteFile 不存在 fileId → FILE_NOT_FOUND。
     */
    @Test
    @Order(21)
    void shouldRejectDeleteNotFound() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.deleteFile(NOT_EXIST_ID, UPLOADER_ID));
        assertEquals(ErrorCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    /**
     * batchGetFileInfo 空列表 → 返回空 List（不报错）。
     */
    @Test
    @Order(22)
    void shouldBatchGetFileInfoReturnEmptyForEmptyList() {
        List<FileInfo> infos = fileService.batchGetFileInfo(List.of(), UPLOADER_ID);
        assertNotNull(infos);
        assertTrue(infos.isEmpty());
    }
}
```

---

## 3. 测试用例矩阵

| #    | 测试方法                                    | 场景                           | 预期                                              |
| ---- | ------------------------------------------- | ------------------------------ | ------------------------------------------------- |
| 1    | `shouldGetUploadURL`                        | 正常获取上传 URL + expiresIn 忽略 | 返回 fileId + uploadUrl + key，DB PENDING，expiresAt > now+60s |
| 2    | `shouldRejectConfirmByNonUploader`          | 非上传者确认（PENDING 状态）   | FILE_NOT_UPLOADER                                 |
| 3    | `shouldConfirmUpload`                       | 正常确认上传                   | status → CONFIRMED                                |
| 4    | `shouldRejectConfirmTwice`                  | 重复确认                       | BAD_REQUEST                                       |
| 5    | `shouldGetDownloadURL`                      | 正常获取下载 URL（非上传者）   | 返回 downloadUrl                                  |
| 6    | `shouldGetFileInfo`                         | 正常查询文件信息               | 返回完整 FileInfo，status=CONFIRMED               |
| 7    | `shouldBatchGetFileInfo`                    | 批量查询（含不存在 ID）        | 只返回 1 条 CONFIRMED                             |
| 8    | `shouldRejectDeleteByNonUploader`           | 非上传者删除                   | FILE_NOT_UPLOADER                                 |
| 9    | `shouldDeleteFile`                           | 正常删除                       | status → DELETED                                  |
| 10   | `shouldRejectDownloadAfterDelete`           | 下载已删除文件                 | FILE_DELETED                                      |
| 11   | `shouldRejectDeleteTwice`                   | 重复删除（幂等）               | FILE_DELETED                                      |
| 12   | `shouldRejectEmptyName`                     | 空文件名                       | BAD_REQUEST                                       |
| 13   | `shouldRejectOversizeImage`                 | 头像 60MB 超 50MB 限制         | FILE_TOO_LARGE                                    |
| 14   | `shouldRejectOversizeAttachment`            | 附件 200MB 超 100MB 限制       | FILE_TOO_LARGE                                    |
| 15   | `shouldRejectExeMime`                       | 可执行文件 MIME                | FILE_TYPE_NOT_SUPPORT                             |
| 16   | `shouldPreventPathTraversal`                | 文件名含 `../../../etc/passwd` | key 不含 `..`/`/etc/`，ext=bin                    |
| 17   | `shouldUseBinForNoExtension`                | 无扩展名文件                   | key 以 `.bin` 结尾，DB ext=bin                    |
| 18   | `shouldRejectConfirmNotFound`              | confirm 不存在 fileId          | FILE_NOT_FOUND                                    |
| 19   | `shouldRejectDownloadPendingFile`           | 下载未确认文件                 | FILE_PENDING                                      |
| 20   | `shouldRejectGetFileInfoNotFound`           | 查询不存在的 fileId            | FILE_NOT_FOUND                                    |
| 21   | `shouldRejectDeleteNotFound`                | 删除不存在的 fileId            | FILE_NOT_FOUND                                    |
| 22   | `shouldBatchGetFileInfoReturnEmptyForEmptyList` | batch 传空列表             | 返回空 List                                       |

---

## 4. 异常场景覆盖

| 异常场景              | 覆盖方法                                    | ErrorCode                |
| --------------------- | ------------------------------------------- | ------------------------ |
| 空文件名              | `shouldRejectEmptyName`                    | BAD_REQUEST              |
| 图片过大（超 50MB）   | `shouldRejectOversizeImage`                 | FILE_TOO_LARGE           |
| 附件过大（超 100MB）  | `shouldRejectOversizeAttachment`            | FILE_TOO_LARGE           |
| 不支持的类型          | `shouldRejectExeMime`                       | FILE_TYPE_NOT_SUPPORT    |
| 路径穿越              | `shouldPreventPathTraversal`                | 不抛异常（静默过滤 ext） |
| 非上传者确认          | `shouldRejectConfirmByNonUploader`         | FILE_NOT_UPLOADER        |
| 重复确认              | `shouldRejectConfirmTwice`                  | BAD_REQUEST              |
| 下载未确认文件        | `shouldRejectDownloadPendingFile`           | FILE_PENDING             |
| 下载已删除文件        | `shouldRejectDownloadAfterDelete`           | FILE_DELETED             |
| 确认不存在文件        | `shouldRejectConfirmNotFound`               | FILE_NOT_FOUND           |
| 查询不存在文件        | `shouldRejectGetFileInfoNotFound`           | FILE_NOT_FOUND           |
| 删除不存在文件        | `shouldRejectDeleteNotFound`                | FILE_NOT_FOUND           |
| 非上传者删除          | `shouldRejectDeleteByNonUploader`            | FILE_NOT_UPLOADER        |
| 重复删除（幂等）      | `shouldRejectDeleteTwice`                   | FILE_DELETED             |
| expiresIn 被忽略      | `shouldGetUploadURL`（正向断言）            | —（不抛异常）            |

---

## 5. 执行命令

```bash
# 1. 确保基础设施运行（PostgreSQL + MinIO 必须可用，Nacos 可选）
docker compose up -d

# 2. 执行 DB schema（首次或 schema 变更后）
docker exec -i aim-postgres psql -U postgres -d aim -f /dev/stdin < backend/docs/sql/aim-schema.sql
# 或本机装了 psql：
#   psql -h localhost -U postgres -d aim -f backend/docs/sql/aim-schema.sql

# 3. 运行全部测试（-am 同时构建依赖 aim-common）
mvn test -pl file-service -am -Dtest=FileServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false

# 4. 只运行单个测试方法
mvn test -pl file-service -am -Dtest=FileServiceImplTest#shouldGetUploadURL -Dsurefire.failIfNoSpecifiedTests=false
```

> **`-am`**：also make 会同时编译 `aim-common`，否则若 common 尚未 `mvn install` 进本地 `~/.m2`，`-pl file-service` 会因依赖解析失败。
> **`-Dsurefire.failIfNoSpecifiedTests=false`**：`-am` 会把 common 也带进测试 reactor；common 中没有命名匹配 `FileServiceImplTest` 的测试类，不加该 flag 会报"未找到指定测试"。

---

## 6. 注意事项

1. **测试顺序敏感**：主链路用例 `@Order(1)` 产生的 `fileId` 被后续用例复用，独立异常用例可单独运行
2. **基础环境**：PostgreSQL + MinIO 必须运行；Nacos / Dubbo 因被隔离**不再依赖**（`TestConfig` 已 exclude）
3. **构造器注入守规范 + 必须加 @Autowired**：测试类构造器注入 `FileService`+`FileMapper`，遵守 AGENTS.md 反字段注入规范；JUnit 5 + `@SpringBootTest` 默认 FIELD 模式，构造器注入需显式 `@Autowired`
4. **BizException.getCode() 返回 int**：断言时用 `ErrorCode.XXX.getCode()` 而非 `ErrorCode.XXX`
5. **DB 清理**：`@AfterAll cleanup()` 硬删 `createdFileIds` 所有行；主链路的 DELETED 软删行也会被 `deleteBatchIds` 物理清除
6. **PENDING 行风险**：Order 16/17/19 会留 PENDING 行，全程运行远短于 30min，不会被 zombie 清理器误删；即便意外误删 @AfterAll 仍能安全 `deleteBatchIds`
7. **MinIO 对象不真实上传**：测试只生成 Presigned URL 不真正 PUT 对象，`deleteFile` 里 `minioClient.removeObject` 去删不存在的对象——MinIO 对此静默返回成功，不影响断言
8. **重复运行幂等**：`fileId` 是 Snowflake 生成，每次运行不重复触发唯一键冲突；加上 `@AfterAll` 硬删，可无限重复执行
9. **zombie 清理**：本测试不覆盖 `cleanupZombieFiles`（需要等待 30 分钟），由 file-service-spec §18.4 手动验证
10. **Dubbo 隔离关键点**：
    - **TestConfig 不继承 FileServiceApplication**：避开启动类上的 `@EnableDubbo`，Dubbo 不会以 Provider 模式启动
    - **exclude Dubbo 自动配置**：`DubboAutoConfiguration` / `DubboListener...` / `DubboRelaxedBinding2...` / `DubboTriple...` 共 4 个，阻止 Dubbo BeanPostProcessor 注册
    - **excludeFilters 排除 rpc 包**：避免 `@DubboService` 注解的 `FileRpcServiceImpl` 被 ComponentScan 触发 Dubbo 启动
    - **test/resources/application.yml 必须存在**：见 §1.2 解释。Dubbo 的 `DubboApplicationContextInitializer` 若在 `target/test-classes/` 没发现 application.yml，会写一个仅含 `dubbo.config-center.address: "N/A"` 的 stub，覆盖 main 配置导致 `spring.datasource.*` 失配
```

---

## 📝 文档结构说明

| 章节 | 内容 |
|------|------|
| §0 设计决策 | 8 项关键决策及理由 |
| §1 依赖添加 | pom.xml 需要追加的依赖 + test/resources/application.yml 必需 |
| §2 测试代码 | 22 个测试方法 + TestConfig 的完整代码 |
| §3 测试矩阵 | 表格化一览 |
| §4 异常覆盖 | 15 种异常场景及对应 ErrorCode |
| §5 执行命令 | 从环境准备到运行的完整命令 |
| §6 注意事项 | 10 条关键提醒（含 Dubbo 隔离和 stub yml 原理）|

主人可以直接把上面代码块的内容复制到 `file-service-test.md` 中喵～ 然后按照 §1 和 §2 的指引，加依赖、创建测试文件、跑 `mvn test` 就行啦 (๑ˉ∀ˉ๑)