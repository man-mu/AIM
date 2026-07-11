package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友信息 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendInfoDTO {

    private long userId;
    private String username;
    private String avatar;
    private String remark;
    private long groupId;
    private String groupName;
    private String status;
    private long createdAt;
}
