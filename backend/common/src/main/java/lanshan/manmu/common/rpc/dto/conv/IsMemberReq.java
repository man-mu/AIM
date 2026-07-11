package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查是否成员请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IsMemberReq {

    private long conversationId;
    private long userId;
}
