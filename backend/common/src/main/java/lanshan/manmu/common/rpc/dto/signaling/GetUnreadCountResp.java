package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取未读通知数量响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUnreadCountResp {

    private long count;
}
