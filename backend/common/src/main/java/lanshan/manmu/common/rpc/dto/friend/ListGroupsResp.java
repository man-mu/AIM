package lanshan.manmu.common.rpc.dto.friend;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 查看分组列表响应（契约 §4：{@code {list, total}}，内置 groupId=0 默认分组）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListGroupsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<FriendGroupDTO> list;
    private long total;
}
