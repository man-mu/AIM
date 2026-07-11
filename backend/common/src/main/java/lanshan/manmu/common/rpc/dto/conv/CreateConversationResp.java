package lanshan.manmu.common.rpc.dto.conv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationResp {

    private long conversationId;
    private ConversationDTO conversation;
}
