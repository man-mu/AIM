package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置备注请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetRemarkReq {

    private long userId;
    private long friendUserId;
    private String remark;
}
