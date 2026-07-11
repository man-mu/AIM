package lanshan.manmu.common.rpc.dto.signaling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看通知列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListNotificationsReq {

    private long userId;
    private int pageNum;
    private int pageSize;
}
