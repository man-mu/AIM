package lanshan.manmu.friend.service;

import lanshan.manmu.common.rpc.dto.friend.*;

public interface FriendService {

    // —— 好友申请 ——
    SendFriendRequestResp sendFriendRequest(SendFriendRequestReq req);
    ListFriendRequestsResp listFriendRequests(ListFriendRequestsReq req);
    FriendRequestDTO acceptFriendRequest(AcceptFriendRequestReq req);
    FriendRequestDTO rejectFriendRequest(RejectFriendRequestReq req);
    void cancelFriendRequest(CancelFriendRequestReq req);

    // —— 好友管理 ——
    ListFriendsResp listFriends(ListFriendsReq req);
    void deleteFriend(DeleteFriendReq req);
    void setRemark(SetRemarkReq req);
    void moveGroup(MoveGroupReq req);

    // —— 好友分组 ——
    ListGroupsResp listGroups(ListGroupsReq req);
    CreateGroupResp createGroup(CreateGroupReq req);
    RenameGroupResp renameGroup(RenameGroupReq req);
    void deleteGroup(DeleteGroupReq req);

    // —— 黑名单 ——
    void blockUser(BlockUserReq req);
    void unblockUser(UnblockUserReq req);
    ListBlacklistResp listBlacklist(ListBlacklistReq req);
    boolean isBlocked(IsBlockedReq req);
}
