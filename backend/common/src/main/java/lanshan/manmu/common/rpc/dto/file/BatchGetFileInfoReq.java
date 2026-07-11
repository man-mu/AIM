package lanshan.manmu.common.rpc.dto.file;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取文件信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetFileInfoReq {

    private List<Long> fileIds;
    private long userId;
}
