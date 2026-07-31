package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 删除好友分组请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteGroupReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private long groupId;
}
