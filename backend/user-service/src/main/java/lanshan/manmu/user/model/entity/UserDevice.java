package lanshan.manmu.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户设备实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_devices")
public class UserDevice {

    @TableId
    private Long id;
    private Long userId;
    private String deviceId;
    private String platform;
    private String pushToken;
    private String ip;
    private String location;
    private OffsetDateTime lastActiveAt;
    private OffsetDateTime createdAt;
}
