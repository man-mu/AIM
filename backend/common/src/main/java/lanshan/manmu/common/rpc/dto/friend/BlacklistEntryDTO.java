package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 黑名单条目 DTO（契约 §4：{@code userId/username/avatar/createdAt}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistEntryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String username;
    private String avatar;
    private long createdAt;
}
