package lanshan.manmu.friend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友分组（friend.friend_groups）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("friend_groups")
public class FriendGroup {
    @TableId
    private Long id;
    private Long userId;
    private String name;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
}
