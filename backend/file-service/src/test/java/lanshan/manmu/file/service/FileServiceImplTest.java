package lanshan.manmu.file.service;

import static org.junit.jupiter.api.Assertions.*;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import lanshan.manmu.common.constant.CommonConst;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.file.*;
import lanshan.manmu.file.config.MinioConfig;
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
     * 通过 {@code exclude} 显式排除 Dubbo 自动配置，避免触发 Nacos 连接与端口占用。
     * 仅保留自动配置 + Mapper 扫描 + 业务 Bean 扫描。
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
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /** 主链路 fileId（getUploadURL→confirm→download→delete） */
    private long fileId;
    /** 未确认文件 fileId（getDownloadURL PENDING 场景） */
    private long pendingFileId;
    /** 所有经 getUploadURL 创建的 fileId，用于 @AfterAll 硬删 */
    private final List<Long> createdFileIds = new ArrayList<>();

    @Autowired
    public FileServiceImplTest(FileService fileService, FileMapper fileMapper,
                               MinioClient minioClient, MinioConfig minioConfig) {
        this.fileService = fileService;
        this.fileMapper = fileMapper;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    /** 向 MinIO 真实上传测试对象（key/bucket 取自 DB 记录） */
    private void uploadObject(long id, byte[] content) {
        FileEntity entity = fileMapper.selectById(id);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(entity.getKey())
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("测试上传对象失败 fileId=" + id, e);
        }
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
     * <p>confirmUpload 现在会 statObject 校验 MinIO 实际大小，故确认前先真实上传对象。
     */
    @Test
    @Order(3)
    void shouldConfirmUpload() {
        // 真实上传 1024 字节（与 getUploadURL 声明一致）
        byte[] content = new byte[1024];
        uploadObject(fileId, content);

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

    // ==================== confirmUpload 服务端实际大小校验（#6） ====================

    /** 新建 PENDING 文件并返回 fileId（声明 size=1024） */
    private long newPendingFile() {
        GetUploadURLReq req = new GetUploadURLReq();
        req.setName("check.jpg");
        req.setSize(1024);
        req.setMimeType("image/jpeg");
        req.setPurpose(1);
        req.setAccess(1);
        req.setUploaderId(UPLOADER_ID);
        return track(fileService.getUploadURL(req).getFileId());
    }

    /**
     * 只申请 URL 未实际上传对象 → confirmUpload 拒绝（FILE_UPLOAD_FAILED）。
     * <p>防僵尸记录：statObject NoSuchKey 时不允许确认。
     */
    @Test
    @Order(23)
    void shouldRejectConfirmObjectNotUploaded() {
        long id = newPendingFile();

        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(id);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.confirmUpload(req));
        assertEquals(ErrorCode.FILE_UPLOAD_FAILED.getCode(), ex.getCode());

        // DB 记录保持 PENDING，未被误确认
        assertEquals(CommonConst.FILE_STATUS_PENDING,
                fileMapper.selectById(id).getStatus());
    }

    /**
     * 实际上传大小超过声明值 → confirmUpload 拒绝（FILE_TOO_LARGE）并删除 MinIO 对象。
     */
    @Test
    @Order(24)
    void shouldRejectConfirmOversizeActual() {
        long id = newPendingFile();
        // 声明 1024 字节，实际上传 2048 字节
        uploadObject(id, new byte[2048]);

        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(id);
        req.setUploaderId(UPLOADER_ID);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.confirmUpload(req));
        assertEquals(ErrorCode.FILE_TOO_LARGE.getCode(), ex.getCode());

        // DB 记录保持 PENDING + MinIO 对象已被清理（statObject 应 NoSuchKey）
        assertEquals(CommonConst.FILE_STATUS_PENDING,
                fileMapper.selectById(id).getStatus());
        assertThrows(Exception.class, () -> minioClient.statObject(
                io.minio.StatObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(fileMapper.selectById(id).getKey())
                        .build()));
    }

    /**
     * 实际大小小于声明值 → 确认成功且 DB size 校正为实际值。
     */
    @Test
    @Order(25)
    void shouldConfirmCorrectionWhenActualSmaller() {
        long id = newPendingFile();
        // 声明 1024 字节，实际上传 100 字节
        uploadObject(id, new byte[100]);

        ConfirmUploadReq req = new ConfirmUploadReq();
        req.setFileId(id);
        req.setUploaderId(UPLOADER_ID);

        ConfirmUploadResp resp = fileService.confirmUpload(req);
        assertEquals(CommonConst.FILE_STATUS_CONFIRMED, resp.getFile().getStatus());
        assertEquals(100L, fileMapper.selectById(id).getSize(),
                "DB size 应以 MinIO 实际大小为准");
    }
}