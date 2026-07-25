package lanshan.manmu.conv.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.conv.*;

public interface ConvService {
    CreateConversationResp createConversation(CreateConversationReq req);
    ConversationDTO getConversation(long conversationId, long userId);
    ListConversationsResp listConversations(ListConversationsReq req);
    AddMembersResp addMembers(AddMembersReq req);
    void removeMembers(RemoveMembersReq req);
    GetMembersResp getMembers(GetMembersReq req);
    boolean isMember(long conversationId, long userId);
    PreCheckSendResp preCheckSend(PreCheckSendReq req);
    void markRead(MarkReadReq req);
    void updateLastMessage(UpdateLastMessageReq req);
    void muteMember(MuteMemberReq req);
    void transferOwner(TransferOwnerReq req);
    void updateAnnouncement(long convId, long operatorId, String content);
    GetSettingsResp getSettings(GetSettingsReq req);
    void updateSettings(UpdateSettingsReq req);
}
