package lanshan.manmu.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId
    private Long id;
    private String username;
    private String passwordHash;
    private String phone;
    private String email;
    private String avatar;
    private Integer gender;
    private String bio;
    private Long birthday;
    private BigDecimal balance;
    private String settings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
