package lanshan.manmu.common.rpc;

import java.util.List;
import lanshan.manmu.common.rpc.dto.message.*;

/**
 * 消息服务 Dubbo 接口。
 */
public interface MessageRpcService {

    // —— 消息核心 ——
    SendMessageResp sendMessage(SendMessageReq req);
    void recallMessage(RecallMessageReq req);
    void editMessage(EditMessageReq req);
    void deleteMessage(DeleteMessageReq req);

    // —— 消息查询 ——
    GetMessagesResp getMessages(GetMessagesReq req);
    SyncMessagesResp syncMessages(SyncMessagesReq req);
    MessageDTO getMessageById(long messageId);
    BatchGetMessagesResp batchGetMessages(List<Long> messageIds);
    SearchMessagesResp searchMessages(SearchMessagesReq req);
}
