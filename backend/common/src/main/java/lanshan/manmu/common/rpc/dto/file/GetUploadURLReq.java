package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取上传 URL 请求。
 * <p>Presigned URL 有效期由服务端固定（见
 * {@link lanshan.manmu.common.constant.CommonConst#FILE_PRESIGN_EXPIRE_SEC}），
 * 不接受客户端传值；请求体中多余的 {@code expiresIn} 字段会被 Jackson 静默忽略。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUploadURLReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String mimeType;
    private long size;
    private long uploaderId;
    private int purpose;
    private int access;
}
