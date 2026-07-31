package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 设置备注请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetRemarkReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long friendUserId;
    private String remark;
}
