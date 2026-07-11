package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查在线状态请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsOnlineReq {

    private long userId;
}
