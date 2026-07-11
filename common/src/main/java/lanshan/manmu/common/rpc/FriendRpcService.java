package lanshan.manmu.common.rpc;

import lanshan.manmu.common.rpc.dto.friend.*;

/**
 * 好友服务 Dubbo 接口。
 */
public interface FriendRpcService {

    // —— 好友申请 ——
    SendFriendRequestResp sendFriendRequest(SendFriendRequestReq req);
    void acceptFriendRequest(AcceptFriendRequestReq req);
    void rejectFriendRequest(RejectFriendRequestReq req);
    void cancelFriendRequest(CancelFriendRequestReq req);
    ListFriendRequestsResp listFriendRequests(ListFriendRequestsReq req);

    // —— 好友管理 ——
    ListFriendsResp listFriends(ListFriendsReq req);
    void deleteFriend(DeleteFriendReq req);
    void setRemark(SetRemarkReq req);

    // —— 好友分组 ——
    ListGroupsResp listGroups(ListGroupsReq req);
    CreateGroupResp createGroup(CreateGroupReq req);
    void renameGroup(RenameGroupReq req);
    void deleteGroup(DeleteGroupReq req);

    // —— 黑名单 ——
    void blockUser(BlockUserReq req);
    void unblockUser(UnblockUserReq req);
    ListBlacklistResp listBlacklist(ListBlacklistReq req);
    boolean isBlocked(IsBlockedReq req);
}
