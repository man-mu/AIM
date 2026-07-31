package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 确认上传响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private FileInfo file;
}
