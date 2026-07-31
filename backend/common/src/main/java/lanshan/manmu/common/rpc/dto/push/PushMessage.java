package lanshan.manmu.common.rpc.dto.push;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * WebSocket 推送消息体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String event;
    private Map<String, Object> data;
    private long timestamp;
}
