package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 添加会话成员响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMembersResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Long> addedUserIds;
    private List<Long> alreadyMemberIds;
}
