package lanshan.manmu.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/**
 * 文件业务核心实现：两阶段上传（预分配 ID + Presigned URL）+ 安全校验 + zombie 清理。
 */
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
                new LambdaQueryWrapper<FileEntity>()
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