package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 搜索消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMessagesReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String keyword;
    private Long conversationId;
    private Long startTime;
    private Long endTime;
    private Long senderId;
    private int pageNum;
    private int pageSize;
}
