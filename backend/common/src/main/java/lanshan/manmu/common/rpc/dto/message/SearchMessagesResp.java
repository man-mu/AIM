package lanshan.manmu.common.rpc.dto.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索消息响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMessagesResp {

    private List<MessageDTO> messages;
    private long total;
}
