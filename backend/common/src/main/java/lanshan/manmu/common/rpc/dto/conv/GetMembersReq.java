package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看会话成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMembersReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private long userId;
    private int pageNum;
    private int pageSize;
}
