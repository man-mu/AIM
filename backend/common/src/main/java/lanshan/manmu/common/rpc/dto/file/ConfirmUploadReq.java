package lanshan.manmu.common.rpc.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认上传请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadReq {

    private long fileId;
    private long uploaderId;
    private String md5;
}
