package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {

    private long id;
    private int type;
    private String name;
    private String avatar;
    private long ownerId;
    private int memberCount;
    private long maxSeq;
    private long lastMessageId;
    private String lastMessagePreview;
    private String announcement;
    private boolean isMutedAll;
    private long createdAt;
    private long updatedAt;
}
