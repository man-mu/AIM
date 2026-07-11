package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取下载 URL 请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetDownloadURLReq {

    private long fileId;
    private long userId;
    private int expiresIn;
}
