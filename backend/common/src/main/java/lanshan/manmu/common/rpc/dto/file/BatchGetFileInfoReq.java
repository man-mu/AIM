package lanshan.manmu.common.rpc.dto.file;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 批量获取文件信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchGetFileInfoReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Long> fileIds;
    private long userId;
}
