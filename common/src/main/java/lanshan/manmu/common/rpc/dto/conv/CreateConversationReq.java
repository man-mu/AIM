package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationReq {

    private int type;
    private long creatorId;
    private Long peerUserId;
    private String name;
    private String avatar;
    private List<Long> memberIds;
}
