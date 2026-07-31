package lanshan.manmu.conv.rpc;

import lanshan.manmu.common.rpc.ConvRpcService;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.conv.service.ConvService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * conv-service Dubbo Provider 实现。
 * <p>纯转发层，不做业务逻辑，签名严格对齐 ConvRpcService 接口（spec 第 13 节）。
 * <p>与 FileRpcServiceImpl 风格完全对齐：构造器注入 ConvService，薄转发。
 */
@DubboService
@Slf4j
public class ConvRpcServiceImpl implements ConvRpcService {

    private final ConvService convService;

    public ConvRpcServiceImpl(ConvService convService) {
        this.convService = convService;
    }

    @Override
    public CreateConversationResp createConversation(CreateConversationReq req) {
        return convService.createConversation(req);
    }

    @Override
    public ConversationDTO getConversation(long conversationId, long userId) {
        return convService.getConversation(conversationId, userId);
    }

    @Override
    public ListConversationsResp listConversations(ListConversationsReq req) {
        return convService.listConversations(req);
    }

    @Override
    public AddMembersResp addMembers(AddMembersReq req) {
        return convService.addMembers(req);
    }

    @Override
    public void removeMembers(RemoveMembersReq req) {
        convService.removeMembers(req);
    }

    @Override
    public GetMembersResp getMembers(GetMembersReq req) {
        return convService.getMembers(req);
    }

    @Override
    public boolean isMember(long conversationId, long userId) {
        return convService.isMember(conversationId, userId);
    }

    @Override
    public PreCheckSendResp preCheckSend(PreCheckSendReq req) {
        return convService.preCheckSend(req);
    }

    @Override
    public void markRead(MarkReadReq req) {
        convService.markRead(req);
    }

    @Override
    public void updateLastMessage(UpdateLastMessageReq req) {
        convService.updateLastMessage(req);
    }

    @Override
    public void muteMember(MuteMemberReq req) {
        convService.muteMember(req);
    }

    @Override
    public void transferOwner(TransferOwnerReq req) {
        convService.transferOwner(req);
    }

    @Override
    public void updateAnnouncement(long convId, long operatorId, String content) {
        convService.updateAnnouncement(convId, operatorId, content);
    }

    @Override
    public GetSettingsResp getSettings(GetSettingsReq req) {
        return convService.getSettings(req);
    }

    @Override
    public void updateSettings(UpdateSettingsReq req) {
        convService.updateSettings(req);
    }
}
