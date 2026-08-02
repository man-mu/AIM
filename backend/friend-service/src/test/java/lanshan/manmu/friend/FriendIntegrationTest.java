package lanshan.manmu.friend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.friend.*;
import lanshan.manmu.friend.service.FriendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * friend-service 集成测试（契约 §4 端到端，Testcontainers PG + Redis）。
 * <p>覆盖：申请流程（幂等/错误码矩阵）、接受/拒绝/取消（权限与并发语义）、
 * 好友管理（列表/过滤/备注/移动/删除）、分组 CRUD（默认组/回落）、
 * 黑名单语义（解除好友/取消申请/幂等）、分页钳制与回显。
 */
class FriendIntegrationTest extends FriendIntegrationTestBase {

    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;
    private static final long USER_C = 1003L;
    private static final long USER_D = 1004L;

    @Autowired
    FriendService friendService;

    // ==================== 好友申请 ====================

    @Test
    void test_sendAcceptFullFlow() {
        // 1. A 向 B 发申请
        SendFriendRequestResp sent = friendService.sendFriendRequest(
                new SendFriendRequestReq(USER_A, USER_B, "hi"));
        assertThat(sent.getRequestId()).isGreaterThan(0);

        // 2. B 的 pending 列表看到 status=1，含双方 username
        ListFriendRequestsResp pending = friendService.listFriendRequests(
                new ListFriendRequestsReq(USER_B, "incoming", 1, 20));
        assertThat(pending.getList()).hasSize(1);
        FriendRequestDTO dto = pending.getList().get(0);
        assertThat(dto.getStatus()).isEqualTo(1);
        assertThat(dto.getFromUserId()).isEqualTo(USER_A);
        assertThat(dto.getFromUsername()).isEqualTo("u1001");
        assertThat(dto.getToUsername()).isEqualTo("u1002");

        // 3. B 接受 → status=2，双方建好友
        FriendRequestDTO accepted = friendService.acceptFriendRequest(
                new AcceptFriendRequestReq(sent.getRequestId(), USER_B));
        assertThat(accepted.getStatus()).isEqualTo(2);

        // 4. 双向好友可见（A 的列表含 B，B 的列表含 A）
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 100)).getList())
                .extracting(FriendInfoDTO::getUserId).containsExactly(USER_B);
        assertThat(friendService.listFriends(new ListFriendsReq(USER_B, null, 1, 100)).getList())
                .extracting(FriendInfoDTO::getUserId).containsExactly(USER_A);

        // 5. 已是好友后再发申请 → 20001
        assertBizCode(() -> friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "again")),
                ErrorCode.FRIEND_ALREADY_EXISTS);

        // 6. A 的 sent 列表看到全状态（含已接受的 status=2）
        ListFriendRequestsResp sentList = friendService.listFriendRequests(
                new ListFriendRequestsReq(USER_A, "outgoing", 1, 20));
        assertThat(sentList.getList()).extracting(FriendRequestDTO::getStatus).containsExactly(2);
    }

    @Test
    void test_sendRequestErrorCodes() {
        // 目标不存在 → 10001（mock requireUser 语义）
        whenUserNotExists(USER_C);
        assertBizCode(() -> friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_C, "")),
                ErrorCode.USER_NOT_FOUND);
        stubDefaultUsers();

        // 发给自己 → 20004
        assertBizCode(() -> friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_A, "")),
                ErrorCode.FRIEND_SELF);

        // A 拉黑 B 后发申请 → 20006（对方已被我拉黑）
        friendService.blockUser(new BlockUserReq(USER_A, USER_B));
        assertBizCode(() -> friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.BLOCKED_BY_YOU);

        // B 拉黑 A 后 A 发申请 → 20007（我被对方拉黑）
        friendService.unblockUser(new UnblockUserReq(USER_A, USER_B));
        friendService.blockUser(new BlockUserReq(USER_B, USER_A));
        assertBizCode(() -> friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "")),
                ErrorCode.BLOCKED_BY_THEM);
    }

    @Test
    void test_sendRequestIdempotent() {
        // 重复发送：同一 requestId，message 刷新，pending 只有 1 条
        SendFriendRequestResp first = friendService.sendFriendRequest(
                new SendFriendRequestReq(USER_A, USER_B, "hello"));
        SendFriendRequestResp second = friendService.sendFriendRequest(
                new SendFriendRequestReq(USER_A, USER_B, "hello again"));
        assertThat(second.getRequestId()).isEqualTo(first.getRequestId());

        ListFriendRequestsResp pending = friendService.listFriendRequests(
                new ListFriendRequestsReq(USER_B, "incoming", 1, 20));
        assertThat(pending.getList()).hasSize(1);
        assertThat(pending.getList().get(0).getMessage()).isEqualTo("hello again");
    }

    @Test
    void test_acceptRejectCancelPermissions() {
        // A→B 申请
        long requestId = friendService.sendFriendRequest(
                new SendFriendRequestReq(USER_A, USER_B, "hi")).getRequestId();

        // 非收件人接受 → 20002
        assertBizCode(() -> friendService.acceptFriendRequest(new AcceptFriendRequestReq(requestId, USER_C)),
                ErrorCode.FRIEND_REQUEST_HANDLED);
        // 非收件人拒绝 → 20002
        assertBizCode(() -> friendService.rejectFriendRequest(new RejectFriendRequestReq(requestId, USER_C)),
                ErrorCode.FRIEND_REQUEST_HANDLED);
        // 非发起人取消 → 20002
        assertBizCode(() -> friendService.cancelFriendRequest(new CancelFriendRequestReq(requestId, USER_B)),
                ErrorCode.FRIEND_REQUEST_HANDLED);

        // 正常拒绝 → status=3，重复拒绝 → 20002（已处理）
        FriendRequestDTO rejected = friendService.rejectFriendRequest(new RejectFriendRequestReq(requestId, USER_B));
        assertThat(rejected.getStatus()).isEqualTo(3);
        assertBizCode(() -> friendService.rejectFriendRequest(new RejectFriendRequestReq(requestId, USER_B)),
                ErrorCode.FRIEND_REQUEST_HANDLED);

        // 取消 → status=4（仅发起人）
        long req2 = friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "x")).getRequestId();
        friendService.cancelFriendRequest(new CancelFriendRequestReq(req2, USER_A));
        assertThat(friendService.listFriendRequests(new ListFriendRequestsReq(USER_A, "outgoing", 1, 20))
                .getList().get(0).getStatus()).isEqualTo(4);
    }

    @Test
    void test_acceptDuplicate_returns20002() {
        // 重复接受 → 20002（条件 UPDATE status=1 的乐观并发语义，反馈 5）
        long requestId = friendService.sendFriendRequest(
                new SendFriendRequestReq(USER_A, USER_B, "hi")).getRequestId();
        friendService.acceptFriendRequest(new AcceptFriendRequestReq(requestId, USER_B));
        assertBizCode(() -> friendService.acceptFriendRequest(new AcceptFriendRequestReq(requestId, USER_B)),
                ErrorCode.FRIEND_REQUEST_HANDLED);
    }

    // ==================== 好友管理 ====================

    @Test
    void test_friendManageLifecycle() {
        makeFriends(USER_A, USER_B);
        makeFriends(USER_A, USER_C);

        // 建分组并移入 B
        long groupId = friendService.createGroup(new CreateGroupReq(USER_A, "同事", 0)).getGroupId();
        friendService.moveGroup(new MoveGroupReq(USER_A, USER_B, groupId));

        // 分组过滤：同事组仅 B
        ListFriendsResp inGroup = friendService.listFriends(new ListFriendsReq(USER_A, groupId, 1, 100));
        assertThat(inGroup.getList()).extracting(FriendInfoDTO::getUserId).containsExactly(USER_B);
        assertThat(inGroup.getList().get(0).getGroupName()).isEqualTo("同事");
        // 缺省/0：全部好友（mock 行为）
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 100)).getList()).hasSize(2);
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, 0L, 1, 100)).getList()).hasSize(2);

        // 备注 + 移动到不存在的分组 → 20005
        friendService.setRemark(new SetRemarkReq(USER_A, USER_B, "老铁"));
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, groupId, 1, 100))
                .getList().get(0).getRemark()).isEqualTo("老铁");
        assertBizCode(() -> friendService.moveGroup(new MoveGroupReq(USER_A, USER_B, 9999L)),
                ErrorCode.FRIEND_GROUP_NOT_FOUND);

        // 删除好友：双向删除；再删 → 20003
        friendService.deleteFriend(new DeleteFriendReq(USER_A, USER_B));
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 100)).getList())
                .extracting(FriendInfoDTO::getUserId).containsExactly(USER_C);
        assertThat(friendService.listFriends(new ListFriendsReq(USER_B, null, 1, 100)).getList()).isEmpty();
        assertBizCode(() -> friendService.deleteFriend(new DeleteFriendReq(USER_A, USER_B)),
                ErrorCode.NOT_FRIEND);
    }

    // ==================== 好友分组 ====================

    @Test
    void test_groupsCrud() {
        // 默认分组内置（groupId=0，createdAt=0），空名建组落"新建分组"
        ListGroupsResp initial = friendService.listGroups(new ListGroupsReq(USER_A));
        assertThat(initial.getList()).extracting(FriendGroupDTO::getGroupId).containsExactly(0L);
        assertThat(initial.getList().get(0).getName()).isEqualTo("默认分组");
        assertThat(initial.getList().get(0).getCreatedAt()).isZero();

        CreateGroupResp created = friendService.createGroup(new CreateGroupReq(USER_A, "  ", 0));
        assertThat(created.getName()).isEqualTo("新建分组");
        long groupId = created.getGroupId();

        // 重命名
        RenameGroupResp renamed = friendService.renameGroup(new RenameGroupReq(USER_A, groupId, "家人"));
        assertThat(renamed.getName()).isEqualTo("家人");

        // 好友移入后 friendCount 正确
        makeFriends(USER_A, USER_B);
        friendService.moveGroup(new MoveGroupReq(USER_A, USER_B, groupId));
        ListGroupsResp groups = friendService.listGroups(new ListGroupsReq(USER_A));
        assertThat(groups.getList()).extracting(FriendGroupDTO::getName).containsExactly("默认分组", "家人");
        assertThat(groups.getList().get(1).getFriendCount()).isEqualTo(1);

        // 删除分组：组内好友回落默认分组；重命名不存在分组 → 20005
        friendService.deleteGroup(new DeleteGroupReq(USER_A, groupId));
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, 0L, 1, 100)).getList())
                .extracting(FriendInfoDTO::getGroupId).containsExactly(0L);
        assertBizCode(() -> friendService.renameGroup(new RenameGroupReq(USER_A, groupId, "x")),
                ErrorCode.FRIEND_GROUP_NOT_FOUND);
        assertBizCode(() -> friendService.deleteGroup(new DeleteGroupReq(USER_A, groupId)),
                ErrorCode.FRIEND_GROUP_NOT_FOUND);
        // 他人分组不可操作 → 20005
        long bGroup = friendService.createGroup(new CreateGroupReq(USER_B, "B组", 0)).getGroupId();
        assertBizCode(() -> friendService.renameGroup(new RenameGroupReq(USER_A, bGroup, "x")),
                ErrorCode.FRIEND_GROUP_NOT_FOUND);
    }

    // ==================== 黑名单 ====================

    @Test
    void test_blockSemantics() {
        // 先建立双方 pending 申请（A→B、B→A 各一条），再接受 B→A 的申请建好友
        long reqAtoB = friendService.sendFriendRequest(new SendFriendRequestReq(USER_A, USER_B, "hi")).getRequestId();
        long reqBtoA = friendService.sendFriendRequest(new SendFriendRequestReq(USER_B, USER_A, "hi")).getRequestId();
        friendService.acceptFriendRequest(new AcceptFriendRequestReq(reqBtoA, USER_A));
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 100)).getList())
                .extracting(FriendInfoDTO::getUserId).containsExactly(USER_B);

        // A 拉黑 B：解除好友 + 取消 status=1 的 pending（A→B 被取消，已接受的 B→A 保持 2）
        friendService.blockUser(new BlockUserReq(USER_A, USER_B));
        assertThat(friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 100)).getList()).isEmpty();
        assertThat(friendService.listFriends(new ListFriendsReq(USER_B, null, 1, 100)).getList()).isEmpty();
        ListFriendRequestsResp outgoing = friendService.listFriendRequests(
                new ListFriendRequestsReq(USER_A, "outgoing", 1, 20));
        assertThat(outgoing.getList()).extracting(FriendRequestDTO::getRequestId).containsExactly(reqAtoB);
        assertThat(outgoing.getList()).extracting(FriendRequestDTO::getStatus).containsExactly(4);
        friendService.blockUser(new BlockUserReq(USER_A, USER_B)); // 重复拉黑幂等不报错

        // isBlocked 方向性
        assertThat(friendService.isBlocked(new IsBlockedReq(USER_A, USER_B))).isTrue();
        assertThat(friendService.isBlocked(new IsBlockedReq(USER_B, USER_A))).isFalse();

        // 黑名单列表：desc 排序 + 用户信息补全
        friendService.blockUser(new BlockUserReq(USER_A, USER_C));
        ListBlacklistResp blacklist = friendService.listBlacklist(new ListBlacklistReq(USER_A, 1, 100));
        assertThat(blacklist.getList()).extracting(BlacklistEntryDTO::getUserId).containsExactly(USER_C, USER_B);
        assertThat(blacklist.getList().get(0).getUsername()).isEqualTo("u1003");

        // 拉黑自己 → 20004
        assertBizCode(() -> friendService.blockUser(new BlockUserReq(USER_A, USER_A)),
                ErrorCode.FRIEND_SELF);

        // 取消拉黑：不在黑名单 → 20008（NOT_BLOCKED，契约同步见 §10）
        friendService.unblockUser(new UnblockUserReq(USER_A, USER_B));
        assertThat(friendService.isBlocked(new IsBlockedReq(USER_A, USER_B))).isFalse();
        assertBizCode(() -> friendService.unblockUser(new UnblockUserReq(USER_A, USER_B)),
                ErrorCode.NOT_BLOCKED);
    }

    // ==================== 分页 ====================

    @Test
    void test_paginationClamp() {
        makeFriends(USER_A, USER_B);
        makeFriends(USER_A, USER_C);

        // pageSize<=0 → 默认 100；>100 → 100；pageNum<1 → 1；响应回显钳制后的值
        ListFriendsResp zero = friendService.listFriends(new ListFriendsReq(USER_A, null, 0, 0));
        assertThat(zero.getList()).hasSize(2);
        assertThat(zero.getPageNum()).isEqualTo(1);
        assertThat(zero.getPageSize()).isEqualTo(100);

        ListFriendsResp huge = friendService.listFriends(new ListFriendsReq(USER_A, null, 1, 1000));
        assertThat(huge.getPageSize()).isEqualTo(100);

        // 申请列表默认 50（契约 §4）：D → A 发一条 pending
        friendService.sendFriendRequest(new SendFriendRequestReq(USER_D, USER_A, "hi"));
        ListFriendRequestsResp requests = friendService.listFriendRequests(
                new ListFriendRequestsReq(USER_A, "incoming", 1, 0));
        assertThat(requests.getList()).hasSize(1);
        assertThat(requests.getPageSize()).isEqualTo(50);
    }

    // ==================== 辅助 ====================

    private void makeFriends(long a, long b) {
        long requestId = friendService.sendFriendRequest(new SendFriendRequestReq(a, b, "hi")).getRequestId();
        friendService.acceptFriendRequest(new AcceptFriendRequestReq(requestId, b));
    }

    /** 覆盖 stub：指定用户不存在（batch 返回空列表） */
    private void whenUserNotExists(long userId) {
        org.mockito.Mockito.when(userRpcService.batchGetUserInfo(
                        new lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq(List.of(userId))))
                .thenReturn(new lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp(List.of()));
    }

    private void stubDefaultUsers() {
        // 恢复基类默认 stub（@BeforeEach 中定义的行为无法直接复用，重新声明）
        org.mockito.Mockito.when(userRpcService.batchGetUserInfo(
                        org.mockito.ArgumentMatchers.any(
                                lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq.class)))
                .thenAnswer(inv -> {
                    lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq req = inv.getArgument(0);
                    List<lanshan.manmu.common.rpc.dto.user.UserInfo> users =
                            req.getUserIds().stream().map(FriendIntegrationTestBase::userInfo).toList();
                    return new lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp(users);
                });
    }

    private void assertBizCode(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(errorCode.getCode()));
    }
}
