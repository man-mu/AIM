package lanshan.manmu.common.rpc.dto.signaling;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看通知列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListNotificationsResp {

    private List<NotificationDTO> notifications;
    private long total;
}
