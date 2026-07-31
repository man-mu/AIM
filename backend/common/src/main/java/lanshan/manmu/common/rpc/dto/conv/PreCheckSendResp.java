package lanshan.manmu.common.rpc.dto.conv;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 发送消息前置校验响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreCheckSendResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean isMember;
    private boolean isMuted;
    private boolean isMutedAll;
    private long muteUntil;
    private int convType;
    private List<Long> memberIds;
}
