package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友分组 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroupDTO {

    private long id;
    private String name;
    private int sortOrder;
    private int friendCount;
}
