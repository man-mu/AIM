package lanshan.manmu.conv.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("conv_read_seqs")
public class ConvReadSeq {
    @TableId
    private Long id;
    private Long convId;
    private Long userId;
    private Long lastReadSeq;
    private OffsetDateTime readAt;
}
