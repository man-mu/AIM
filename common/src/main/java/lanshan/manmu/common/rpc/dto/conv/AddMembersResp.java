package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加会话成员响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMembersResp {

    private List<Long> addedUserIds;
    private List<Long> alreadyMemberIds;
}
