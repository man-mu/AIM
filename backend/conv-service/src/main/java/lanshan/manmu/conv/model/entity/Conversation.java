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
@TableName("conversations")
public class Conversation {
    @TableId
    private Long id;
    private Integer type;
    private String name;
    private String avatar;
    private Long ownerId;
    private String announcement;
    @TableField("is_muted_all")
    private Boolean isMutedAll;
    private String background;
    private Long maxSeq;
    private Long lastMessageId;
    private String lastMessagePreview;
    private Integer memberCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
