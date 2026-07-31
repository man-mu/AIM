package lanshan.manmu.conv.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_settings")
public class ConvSettings {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    @TableField("is_muted")
    private Boolean isMuted;
    @TableField("is_pinned")
    private Boolean isPinned;
}
