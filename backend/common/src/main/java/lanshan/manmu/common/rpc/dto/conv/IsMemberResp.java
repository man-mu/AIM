package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查是否成员响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsMemberResp {

    private boolean isMember;
}
