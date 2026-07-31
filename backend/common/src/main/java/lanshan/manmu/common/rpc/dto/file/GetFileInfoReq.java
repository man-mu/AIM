package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取文件信息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetFileInfoReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fileId;
    private long userId;
}
