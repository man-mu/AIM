package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDTO {

    private long requestId;
    private long fromUserId;
    private String fromUsername;
    private String fromAvatar;
    private long toUserId;
    private String message;
    private int status;
    private long createdAt;
    private long updatedAt;
}
