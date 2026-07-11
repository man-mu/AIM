package lanshan.manmu.common.rpc.dto.push;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 推送消息体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushMessage {

    private String event;
    private Map<String, Object> data;
    private long timestamp;
}
