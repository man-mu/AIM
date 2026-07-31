package lanshan.manmu.conv.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_members")
public class ConversationMember {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    private String memberType;
    private Long botId;
    private Integer role;
    private String alias;
    @TableField("is_muted")
    private Boolean isMuted;
    private Long muteUntil;
    private OffsetDateTime joinedAt;
}
