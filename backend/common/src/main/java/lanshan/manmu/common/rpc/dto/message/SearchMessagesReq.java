package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMessagesReq {

    private long userId;
    private String keyword;
    private Long conversationId;
    private Long startTime;
    private Long endTime;
    private Long senderId;
    private int pageNum;
    private int pageSize;
}
