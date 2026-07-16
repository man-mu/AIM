package lanshan.manmu.file.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.file.*;

/**
 * 文件业务服务接口。
 */
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