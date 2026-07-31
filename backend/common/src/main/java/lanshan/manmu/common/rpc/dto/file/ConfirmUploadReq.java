package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 确认上传请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long fileId;
    private long uploaderId;
    private String md5;
}
