package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 好友分组 DTO（契约 §4：{@code groupId/name/friendCount/createdAt}；groupId=0 为内置默认分组）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroupDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long groupId;
    private String name;
    private int friendCount;
    private long createdAt;
}
