package lanshan.manmu.common.rpc.dto.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 重命名好友分组响应（契约 §4：{@code {groupId, name}}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameGroupResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long groupId;
    private String name;
}
