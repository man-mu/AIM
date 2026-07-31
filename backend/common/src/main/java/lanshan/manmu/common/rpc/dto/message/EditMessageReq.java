package lanshan.manmu.common.rpc.dto.message;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 编辑消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long messageId;
    private long conversationId;
    private long userId;
    private Map<String, Object> newContent;
}
