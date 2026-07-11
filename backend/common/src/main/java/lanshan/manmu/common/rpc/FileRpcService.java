package lanshan.manmu.common.rpc;

import lanshan.manmu.common.rpc.dto.file.*;

/**
 * 文件服务 Dubbo 接口。
 */
public interface FileRpcService {

    GetUploadURLResp getUploadURL(GetUploadURLReq req);
    ConfirmUploadResp confirmUpload(ConfirmUploadReq req);
    GetDownloadURLResp getDownloadURL(GetDownloadURLReq req);
    GetFileInfoResp getFileInfo(GetFileInfoReq req);
    BatchGetFileInfoResp batchGetFileInfo(BatchGetFileInfoReq req);
    void deleteFile(DeleteFileReq req);
}
