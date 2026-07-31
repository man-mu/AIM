package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 好友分组 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroupDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private int sortOrder;
    private int friendCount;
}
