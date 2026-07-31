package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 删除文件请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFileReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fileId;
    private long userId;
}
