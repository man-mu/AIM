package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查在线状态响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsOnlineResp {

    private boolean online;
}
