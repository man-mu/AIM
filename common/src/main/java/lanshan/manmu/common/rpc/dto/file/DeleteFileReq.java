package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除文件请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFileReq {

    private long fileId;
    private long userId;
}
