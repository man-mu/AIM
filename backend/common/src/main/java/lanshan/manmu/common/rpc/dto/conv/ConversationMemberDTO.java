package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话成员 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemberDTO {

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
