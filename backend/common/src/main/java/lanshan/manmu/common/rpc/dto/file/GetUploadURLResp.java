package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取上传 URL 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUploadURLResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fileId;
    private String uploadUrl;
    private String key;
    private long expiresAt;
}
