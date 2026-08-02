package lanshan.manmu.friend.rpc;

import lanshan.manmu.common.rpc.FriendRpcService;
import lanshan.manmu.common.rpc.dto.friend.*;
import lanshan.manmu.friend.service.FriendService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * friend-service Dubbo Provider 实现。
 * <p>纯转发层，不做业务逻辑，签名严格对齐 FriendRpcService 接口。
 * <p>与 ConvRpcServiceImpl 风格一致：构造器注入 FriendService，薄转发。
 * <p>注：accept/reject 的 HTTP 接口（契约 §4）需返回 FriendRequestDTO，RPC 方法签名为 void，
 * 由 Controller 直接调用 FriendService 获取返回值。
 */
@DubboService
@Slf4j
public class FriendRpcServiceImpl implements FriendRpcService {

    private final FriendService friendService;

    public FriendRpcServiceImpl(FriendService friendService) {
        this.friendService = friendService;
    }

    @Override
    public SendFriendRequestResp sendFriendRequest(SendFriendRequestReq req) {
        return friendService.sendFriendRequest(req);
    }

    @Override
    public void acceptFriendRequest(AcceptFriendRequestReq req) {
        friendService.acceptFriendRequest(req);
    }

    @Override
    public void rejectFriendRequest(RejectFriendRequestReq req) {
        friendService.rejectFriendRequest(req);
    }

    @Override
    public void cancelFriendRequest(CancelFriendRequestReq req) {
        friendService.cancelFriendRequest(req);
    }

    @Override
    public ListFriendRequestsResp listFriendRequests(ListFriendRequestsReq req) {
        return friendService.listFriendRequests(req);
    }

    @Override
    public ListFriendsResp listFriends(ListFriendsReq req) {
        return friendService.listFriends(req);
    }

    @Override
    public void deleteFriend(DeleteFriendReq req) {
        friendService.deleteFriend(req);
    }

    @Override
    public void setRemark(SetRemarkReq req) {
        friendService.setRemark(req);
    }

    @Override
    public void moveGroup(MoveGroupReq req) {
        friendService.moveGroup(req);
    }

    @Override
    public ListGroupsResp listGroups(ListGroupsReq req) {
        return friendService.listGroups(req);
    }

    @Override
    public CreateGroupResp createGroup(CreateGroupReq req) {
        return friendService.createGroup(req);
    }

    @Override
    public RenameGroupResp renameGroup(RenameGroupReq req) {
        return friendService.renameGroup(req);
    }

    @Override
    public void deleteGroup(DeleteGroupReq req) {
        friendService.deleteGroup(req);
    }

    @Override
    public void blockUser(BlockUserReq req) {
        friendService.blockUser(req);
    }

    @Override
    public void unblockUser(UnblockUserReq req) {
        friendService.unblockUser(req);
    }

    @Override
    public ListBlacklistResp listBlacklist(ListBlacklistReq req) {
        return friendService.listBlacklist(req);
    }

    @Override
    public boolean isBlocked(IsBlockedReq req) {
        return friendService.isBlocked(req);
    }
}
