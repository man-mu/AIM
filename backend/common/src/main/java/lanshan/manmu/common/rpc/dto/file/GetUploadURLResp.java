package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取上传 URL 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUploadURLResp {

    private long fileId;
    private String uploadUrl;
    private String key;
    private long expiresAt;
}
