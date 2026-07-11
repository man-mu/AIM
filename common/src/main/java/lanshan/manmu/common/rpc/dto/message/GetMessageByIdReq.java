package lanshan.manmu.common.rpc.dto.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 根据 ID 获取消息请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetMessageByIdReq {

    private long messageId;
}
