package lanshan.manmu.file.controller;

import java.util.List;
import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.file.ConfirmUploadReq;
import lanshan.manmu.common.rpc.dto.file.ConfirmUploadResp;
import lanshan.manmu.common.rpc.dto.file.FileInfo;
import lanshan.manmu.common.rpc.dto.file.GetDownloadURLReq;
import lanshan.manmu.common.rpc.dto.file.GetDownloadURLResp;
import lanshan.manmu.common.rpc.dto.file.GetUploadURLReq;
import lanshan.manmu.common.rpc.dto.file.GetUploadURLResp;
import lanshan.manmu.file.service.FileService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件 Controller（spec controller-spec.md §5.3）。
 * <p>路径对齐 api-v1.md §7：{@code /api/v1/files/**}。
 * <p>所有接口都需鉴权，从网关注入的 {@code X-User-Id} header 取当前用户。
 * <p>API 文档字段 ↔ DTO 字段映射：
 * <ul>
 *   <li>upload-url/uploaderId → 覆盖为 X-User-Id（网关已鉴权，忽略前端传值）</li>
 *   <li>confirm/uploaderId → 覆盖为 X-User-Id</li>
 *   <li>download/userId → 从 X-User-Id 取（不暴露 query 中的 userId）</li>
 *   <li>download → Presigned URL 有效期服务端固定（FILE_PRESIGN_EXPIRE_SEC），不接受客户端传值</li>
 *   <li>delete → fileId 取自 path，userId 取自 X-User-Id（DELETE 不用 body，与 api-v1.md §7.5 略有偏差但更 RESTful）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload-url")
    public Result<GetUploadURLResp> uploadUrl(@RequestHeader("X-User-Id") long userId,
                                               @RequestBody GetUploadURLReq req) {
        req.setUploaderId(userId);
        return Result.ok(fileService.getUploadURL(req));
    }

    @PostMapping("/confirm")
    public Result<ConfirmUploadResp> confirm(@RequestHeader("X-User-Id") long userId,
                                              @RequestBody ConfirmUploadReq req) {
        req.setUploaderId(userId);
        return Result.ok(fileService.confirmUpload(req));
    }

    @GetMapping("/{fileId}/download")
    public Result<GetDownloadURLResp> download(@RequestHeader("X-User-Id") long userId,
                                                @PathVariable("fileId") long fileId) {
        // Presigned URL 有效期由服务端固定（FILE_PRESIGN_EXPIRE_SEC），忽略客户端可能携带的 expiresIn 查询参数
        GetDownloadURLReq req = new GetDownloadURLReq(fileId, userId);
        return Result.ok(fileService.getDownloadURL(req));
    }

    @GetMapping("/{fileId}/info")
    public Result<FileInfo> info(@RequestHeader("X-User-Id") long userId,
                                  @PathVariable("fileId") long fileId) {
        return Result.ok(fileService.getFileInfo(fileId, userId));
    }

    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@RequestHeader("X-User-Id") long userId,
                                @PathVariable("fileId") long fileId) {
        fileService.deleteFile(fileId, userId);
        return Result.ok();
    }

    @PostMapping("/batch")
    public Result<List<FileInfo>> batch(@RequestHeader("X-User-Id") long userId,
                                         @RequestBody List<Long> fileIds) {
        return Result.ok(fileService.batchGetFileInfo(fileIds, userId));
    }
}
