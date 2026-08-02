package lanshan.manmu.friend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友关系（friend.friends，双向各一条记录：user_id 视角 → friend_id）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("friends")
public class Friend {
    @TableId
    private Long id;
    private Long userId;
    private Long friendId;
    private Long groupId;
    private String remark;
    private OffsetDateTime createdAt;
}
