package lanshan.manmu.common.rpc.dto.signaling;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 批量检查在线状态响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchIsOnlineResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<Long, Boolean> status;
}
