package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认上传响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadResp {

    private FileInfo file;
}
