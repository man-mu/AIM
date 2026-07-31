package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看会话列表请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListConversationsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private int pageNum;
    private int pageSize;
}
