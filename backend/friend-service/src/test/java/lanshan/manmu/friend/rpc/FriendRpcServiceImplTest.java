package lanshan.manmu.friend.rpc;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import lanshan.manmu.common.rpc.dto.friend.*;
import lanshan.manmu.friend.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * FriendRpcServiceImpl 单测。
 * <p>纯转发层，验证：每个方法严格转发给 FriendService，不丢参数。
 */
class FriendRpcServiceImplTest {

    private FriendService friendService;
    private FriendRpcServiceImpl rpcService;

    @BeforeEach
    void setUp() {
        friendService = Mockito.mock(FriendService.class);
        rpcService = new FriendRpcServiceImpl(friendService);
    }

    @Test
    void sendFriendRequest_forwardsToService() {
        SendFriendRequestReq req = new SendFriendRequestReq(100L, 200L, "hi");
        SendFriendRequestResp expected = new SendFriendRequestResp(9001L);
        when(friendService.sendFriendRequest(req)).thenReturn(expected);

        assertSame(expected, rpcService.sendFriendRequest(req));
        verify(friendService).sendFriendRequest(req);
    }

    @Test
    void acceptFriendRequest_forwardsToService() {
        AcceptFriendRequestReq req = new AcceptFriendRequestReq(5001L, 200L);
        rpcService.acceptFriendRequest(req);
        verify(friendService).acceptFriendRequest(req);
    }

    @Test
    void rejectFriendRequest_forwardsToService() {
        RejectFriendRequestReq req = new RejectFriendRequestReq(5001L, 200L);
        rpcService.rejectFriendRequest(req);
        verify(friendService).rejectFriendRequest(req);
    }

    @Test
    void cancelFriendRequest_forwardsToService() {
        CancelFriendRequestReq req = new CancelFriendRequestReq(5001L, 100L);
        rpcService.cancelFriendRequest(req);
        verify(friendService).cancelFriendRequest(req);
    }

    @Test
    void listFriendRequests_forwardsToService() {
        ListFriendRequestsReq req = new ListFriendRequestsReq(100L, "incoming", 1, 50);
        ListFriendRequestsResp expected = new ListFriendRequestsResp(List.of(), 0L, 1, 50);
        when(friendService.listFriendRequests(req)).thenReturn(expected);

        assertSame(expected, rpcService.listFriendRequests(req));
        verify(friendService).listFriendRequests(req);
    }

    @Test
    void listFriends_forwardsToService() {
        ListFriendsReq req = new ListFriendsReq(100L, null, 1, 100);
        ListFriendsResp expected = new ListFriendsResp(List.of(), 0L, 1, 100);
        when(friendService.listFriends(req)).thenReturn(expected);

        assertSame(expected, rpcService.listFriends(req));
        verify(friendService).listFriends(req);
    }

    @Test
    void deleteFriend_forwardsToService() {
        DeleteFriendReq req = new DeleteFriendReq(100L, 200L);
        rpcService.deleteFriend(req);
        verify(friendService).deleteFriend(req);
    }

    @Test
    void setRemark_forwardsToService() {
        SetRemarkReq req = new SetRemarkReq(100L, 200L, "老铁");
        rpcService.setRemark(req);
        verify(friendService).setRemark(req);
    }

    @Test
    void moveGroup_forwardsToService() {
        MoveGroupReq req = new MoveGroupReq(100L, 200L, 300L);
        rpcService.moveGroup(req);
        verify(friendService).moveGroup(req);
    }

    @Test
    void listGroups_forwardsToService() {
        ListGroupsReq req = new ListGroupsReq(100L);
        ListGroupsResp expected = new ListGroupsResp(List.of(), 1L);
        when(friendService.listGroups(req)).thenReturn(expected);

        assertSame(expected, rpcService.listGroups(req));
        verify(friendService).listGroups(req);
    }

    @Test
    void createGroup_forwardsToService() {
        CreateGroupReq req = new CreateGroupReq(100L, "家人", 0);
        CreateGroupResp expected = new CreateGroupResp(9001L, "家人");
        when(friendService.createGroup(req)).thenReturn(expected);

        assertSame(expected, rpcService.createGroup(req));
        verify(friendService).createGroup(req);
    }

    @Test
    void renameGroup_forwardsToService() {
        RenameGroupReq req = new RenameGroupReq(100L, 300L, "同事");
        RenameGroupResp expected = new RenameGroupResp(300L, "同事");
        when(friendService.renameGroup(req)).thenReturn(expected);

        assertSame(expected, rpcService.renameGroup(req));
        verify(friendService).renameGroup(req);
    }

    @Test
    void deleteGroup_forwardsToService() {
        DeleteGroupReq req = new DeleteGroupReq(100L, 300L);
        rpcService.deleteGroup(req);
        verify(friendService).deleteGroup(req);
    }

    @Test
    void blockUser_forwardsToService() {
        BlockUserReq req = new BlockUserReq(100L, 200L);
        rpcService.blockUser(req);
        verify(friendService).blockUser(req);
    }

    @Test
    void unblockUser_forwardsToService() {
        UnblockUserReq req = new UnblockUserReq(100L, 200L);
        rpcService.unblockUser(req);
        verify(friendService).unblockUser(req);
    }

    @Test
    void listBlacklist_forwardsToService() {
        ListBlacklistReq req = new ListBlacklistReq(100L, 1, 100);
        ListBlacklistResp expected = new ListBlacklistResp(List.of(), 0L, 1, 100);
        when(friendService.listBlacklist(req)).thenReturn(expected);

        assertSame(expected, rpcService.listBlacklist(req));
        verify(friendService).listBlacklist(req);
    }

    @Test
    void isBlocked_forwardsToService_returnsBoolean() {
        IsBlockedReq req = new IsBlockedReq(100L, 200L);
        when(friendService.isBlocked(req)).thenReturn(true);

        assertTrue(rpcService.isBlocked(req));
        verify(friendService).isBlocked(req);
    }
}
