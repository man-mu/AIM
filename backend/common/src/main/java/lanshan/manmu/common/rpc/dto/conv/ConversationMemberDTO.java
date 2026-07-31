package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 会话成员 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long userId;
    private String username;
    private String avatar;
    private int role;
    private String alias;
    private long joinedAt;
    private long lastReadSeq;
    private boolean isMuted;
    private long muteUntil;
    private int memberType;
    private long botId;
}
