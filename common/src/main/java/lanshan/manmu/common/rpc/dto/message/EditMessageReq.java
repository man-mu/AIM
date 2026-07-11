package lanshan.manmu.common.rpc.dto.message;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编辑消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageReq {

    private long messageId;
    private long conversationId;
    private long userId;
    private Map<String, Object> newContent;
}
