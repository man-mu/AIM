package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取下载 URL 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDownloadURLResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String downloadUrl;
    private long expiresAt;
    private FileInfo file;
}
