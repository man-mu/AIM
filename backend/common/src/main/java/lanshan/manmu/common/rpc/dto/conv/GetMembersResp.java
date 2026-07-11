package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查看会话成员响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMembersResp {

    private List<ConversationMemberDTO> members;
    private long total;
}
