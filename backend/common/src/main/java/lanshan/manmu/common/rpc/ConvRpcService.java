package lanshan.manmu.common.rpc;

import java.util.List;
import lanshan.manmu.common.rpc.dto.conv.*;

/**
 * 会话服务 Dubbo 接口。
 */
public interface ConvRpcService {

    // —— 会话 CRUD ——
    CreateConversationResp createConversation(CreateConversationReq req);
    ConversationDTO getConversation(long conversationId, long userId);
    ListConversationsResp listConversations(ListConversationsReq req);

    // —— 成员管理 ——
    AddMembersResp addMembers(AddMembersReq req);
    void removeMembers(RemoveMembersReq req);
    GetMembersResp getMembers(GetMembersReq req);

    // —— 权限校验（message-service 调用）——
    boolean isMember(long conversationId, long userId);
    PreCheckSendResp preCheckSend(PreCheckSendReq req);

    // —— 已读状态 ——
    void markRead(MarkReadReq req);

    // —— last message 更新（message-service 消费事件后回调）——
    void updateLastMessage(UpdateLastMessageReq req);

    // —— 群管理 ——
    void muteMember(MuteMemberReq req);
    void transferOwner(TransferOwnerReq req);
    void updateAnnouncement(long convId, long operatorId, String content);

    // —— 设置 ——
    GetSettingsResp getSettings(GetSettingsReq req);
    void updateSettings(UpdateSettingsReq req);
}
