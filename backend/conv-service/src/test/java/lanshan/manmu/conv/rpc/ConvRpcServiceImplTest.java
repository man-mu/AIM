package lanshan.manmu.conv.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import lanshan.manmu.common.rpc.dto.conv.*;
import lanshan.manmu.conv.service.ConvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ConvRpcServiceImpl 单测（spec 第 18.1 节）。
 * <p>纯转发层（spec 第 13 节），验证：每个方法严格转发给 ConvService，不丢参数。
 */
class ConvRpcServiceImplTest {

    private ConvService convService;
    private ConvRpcServiceImpl rpcService;

    @BeforeEach
    void setUp() {
        convService = Mockito.mock(ConvService.class);
        rpcService = new ConvRpcServiceImpl(convService);
    }

    @Test
    void createConversation_forwardsToService() {
        CreateConversationReq req = new CreateConversationReq(1, 100L, 200L, "g", "a", List.of());
        CreateConversationResp expected = new CreateConversationResp(9001L, new ConversationDTO());
        when(convService.createConversation(req)).thenReturn(expected);

        CreateConversationResp result = rpcService.createConversation(req);

        assertSame(expected, result);
        verify(convService).createConversation(req);
    }

    @Test
    void getConversation_forwardsLongParams() {
        ConversationDTO expected = new ConversationDTO();
        when(convService.getConversation(1001L, 2001L)).thenReturn(expected);

        ConversationDTO result = rpcService.getConversation(1001L, 2001L);

        assertSame(expected, result);
        verify(convService).getConversation(1001L, 2001L);
    }

    @Test
    void listConversations_forwardsToService() {
        ListConversationsReq req = new ListConversationsReq(100L, 1, 20);
        ListConversationsResp expected = new ListConversationsResp(List.of(), 0L);
        when(convService.listConversations(req)).thenReturn(expected);

        ListConversationsResp result = rpcService.listConversations(req);

        assertSame(expected, result);
        verify(convService).listConversations(req);
    }

    @Test
    void addMembers_forwardsToService() {
        AddMembersReq req = new AddMembersReq(1001L, 100L, List.of(200L));
        AddMembersResp expected = new AddMembersResp(List.of(200L), List.of());
        when(convService.addMembers(req)).thenReturn(expected);

        AddMembersResp result = rpcService.addMembers(req);

        assertSame(expected, result);
        verify(convService).addMembers(req);
    }

    @Test
    void removeMembers_forwardsToService() {
        RemoveMembersReq req = new RemoveMembersReq(1001L, 100L, List.of(200L));
        rpcService.removeMembers(req);
        verify(convService).removeMembers(req);
    }

    @Test
    void getMembers_forwardsToService() {
        GetMembersReq req = new GetMembersReq(1001L, 100L, 1, 20);
        GetMembersResp expected = new GetMembersResp(List.of(), 0L);
        when(convService.getMembers(req)).thenReturn(expected);

        GetMembersResp result = rpcService.getMembers(req);

        assertSame(expected, result);
        verify(convService).getMembers(req);
    }

    @Test
    void isMember_forwardsLongParams_returnsBoolean() {
        when(convService.isMember(1001L, 2001L)).thenReturn(true);

        assertTrue(rpcService.isMember(1001L, 2001L));
        verify(convService).isMember(1001L, 2001L);
    }

    @Test
    void preCheckSend_forwardsToService() {
        PreCheckSendReq req = new PreCheckSendReq(1001L, 2001L);
        PreCheckSendResp expected = new PreCheckSendResp(true, false, false, 0L, 1, List.of());
        when(convService.preCheckSend(req)).thenReturn(expected);

        PreCheckSendResp result = rpcService.preCheckSend(req);

        assertSame(expected, result);
        verify(convService).preCheckSend(req);
    }

    @Test
    void markRead_forwardsToService() {
        MarkReadReq req = new MarkReadReq(100L, 1001L, 50L);
        rpcService.markRead(req);
        verify(convService).markRead(req);
    }

    @Test
    void updateLastMessage_forwardsToService() {
        UpdateLastMessageReq req = new UpdateLastMessageReq(1001L, 9001L, 100L, "hi");
        rpcService.updateLastMessage(req);
        verify(convService).updateLastMessage(req);
    }

    @Test
    void muteMember_forwardsToService() {
        MuteMemberReq req = new MuteMemberReq(1001L, 100L, 200L, 99999L);
        rpcService.muteMember(req);
        verify(convService).muteMember(req);
    }

    @Test
    void transferOwner_forwardsToService() {
        TransferOwnerReq req = new TransferOwnerReq(1001L, 100L, 200L);
        rpcService.transferOwner(req);
        verify(convService).transferOwner(req);
    }

    @Test
    void updateAnnouncement_forwardsThreeParams() {
        rpcService.updateAnnouncement(1001L, 100L, "新公告");
        verify(convService).updateAnnouncement(1001L, 100L, "新公告");
    }

    @Test
    void getSettings_forwardsToService() {
        GetSettingsReq req = new GetSettingsReq(100L, 1001L);
        GetSettingsResp expected = new GetSettingsResp(false, false, "");
        when(convService.getSettings(req)).thenReturn(expected);

        GetSettingsResp result = rpcService.getSettings(req);

        assertSame(expected, result);
        verify(convService).getSettings(req);
    }

    @Test
    void updateSettings_forwardsToService() {
        UpdateSettingsReq req = new UpdateSettingsReq(100L, 1001L, true, null, null);
        rpcService.updateSettings(req);
        verify(convService).updateSettings(req);
    }
}
