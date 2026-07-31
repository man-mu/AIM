package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取消息列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMessagesReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long userId;
    private String cursor;
    private int limit;
    private Long beforeTime;
    private Long afterTime;
}
