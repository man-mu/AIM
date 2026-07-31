package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看通知列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListNotificationsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private int pageNum;
    private int pageSize;
}
