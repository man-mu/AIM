package lanshan.manmu.common.rpc.dto.signaling;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看通知列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListNotificationsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<NotificationDTO> notifications;
    private long total;
}
