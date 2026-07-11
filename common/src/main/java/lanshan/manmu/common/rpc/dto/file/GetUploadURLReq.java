package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取上传 URL 请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUploadURLReq {

    private String name;
    private String mimeType;
    private long size;
    private long uploaderId;
    private int purpose;
    private int access;
    private int expiresIn;
}
