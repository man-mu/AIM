package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息前置校验响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreCheckSendResp {

    private boolean isMember;
    private boolean isMuted;
    private boolean isMutedAll;
    private long muteUntil;
    private int convType;
    private List<Long> memberIds;
}
