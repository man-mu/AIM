package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 创建会话响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private long conversationId;
    private ConversationDTO conversation;
}
