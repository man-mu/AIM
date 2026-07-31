package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取下载 URL 请求。
 * <p>Presigned URL 有效期由服务端固定（见
 * {@link lanshan.manmu.common.constant.CommonConst#FILE_PRESIGN_EXPIRE_SEC}），
 * 不接受客户端传值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDownloadURLReq {

    private long fileId;
    private long userId;
}
