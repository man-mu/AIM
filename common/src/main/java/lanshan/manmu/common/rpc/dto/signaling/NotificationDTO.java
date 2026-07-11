package lanshan.manmu.common.rpc.dto.signaling;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private long id;
    private long userId;
    private int type;
    private String title;
    private String content;
    private boolean isRead;
    private String referenceId;
    private long createdAt;
}
