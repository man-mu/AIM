package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取下载 URL 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDownloadURLResp {

    private String downloadUrl;
    private long expiresAt;
    private FileInfo file;
}
