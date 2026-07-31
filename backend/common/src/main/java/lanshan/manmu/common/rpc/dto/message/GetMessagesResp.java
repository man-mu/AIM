package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 获取消息列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMessagesResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<MessageDTO> messages;
    private String nextCursor;
    private boolean hasMore;
    private long total;
}
