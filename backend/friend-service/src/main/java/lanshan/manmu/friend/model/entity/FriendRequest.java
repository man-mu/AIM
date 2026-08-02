package lanshan.manmu.friend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请（friend.friend_requests；status 1=待处理 2=已接受 3=已拒绝 4=已取消）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("friend_requests")
public class FriendRequest {
    @TableId
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String message;
    private Integer status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
