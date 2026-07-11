package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取文件信息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetFileInfoResp {

    private FileInfo file;
}
