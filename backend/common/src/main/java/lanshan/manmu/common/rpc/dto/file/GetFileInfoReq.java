package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取文件信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetFileInfoReq {

    private long fileId;
    private long userId;
}
