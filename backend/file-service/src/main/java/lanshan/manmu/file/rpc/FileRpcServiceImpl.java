package lanshan.manmu.file.rpc;

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