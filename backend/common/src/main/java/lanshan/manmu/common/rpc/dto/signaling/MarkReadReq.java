package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标记通知已读请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadReq {

    private long notificationId;
    private long userId;
}
