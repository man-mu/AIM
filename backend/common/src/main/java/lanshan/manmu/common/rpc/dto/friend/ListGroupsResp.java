package lanshan.manmu.common.rpc.dto.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看分组列表响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListGroupsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<FriendGroupDTO> groups;
}
