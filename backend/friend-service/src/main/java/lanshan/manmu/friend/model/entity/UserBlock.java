package lanshan.manmu.friend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拉黑关系（friend.user_blocks，单方向：user_id 拉黑 blocked_user_id）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_blocks")
public class UserBlock {
    @TableId
    private Long id;
    private Long userId;
    private Long blockedUserId;
    private OffsetDateTime createdAt;
}
