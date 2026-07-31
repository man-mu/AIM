package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 重命名好友分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameGroupReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long groupId;
    private String name;
}
