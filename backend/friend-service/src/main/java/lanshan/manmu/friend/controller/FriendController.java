package lanshan.manmu.friend.controller;

import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.friend.*;
import lanshan.manmu.friend.service.FriendService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 好友 Controller（spec controller-spec.md）。
 * <p>路径对齐 api-v1.md §4：{@code /api/v1/friends/**}。
 * <p>所有接口都需鉴权，从网关注入的 {@code X-User-Id} header 取当前用户。
 * <p>请求体为本地内部类（字段名对齐契约），校验通过后转换为 common DTO 调用 service 层
 * （与 conv-service ConvController 同款模式）。
 */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    // ==================== 好友申请 ====================

    @PostMapping("/requests")
    public Result<SendFriendRequestResp> sendRequest(@RequestHeader("X-User-Id") long userId,
                                                      @RequestBody SendBody body) {
        SendFriendRequestReq req = new SendFriendRequestReq(userId, body.getToUserId(), body.getMessage());
        return Result.ok(friendService.sendFriendRequest(req));
    }

    @GetMapping("/requests/pending")
    public Result<ListFriendRequestsResp> pending(@RequestHeader("X-User-Id") long userId,
                                                   @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        ListFriendRequestsReq req = new ListFriendRequestsReq(userId, "incoming", pageNum, pageSize);
        return Result.ok(friendService.listFriendRequests(req));
    }

    @GetMapping("/requests/sent")
    public Result<ListFriendRequestsResp> sent(@RequestHeader("X-User-Id") long userId,
                                                @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        ListFriendRequestsReq req = new ListFriendRequestsReq(userId, "outgoing", pageNum, pageSize);
        return Result.ok(friendService.listFriendRequests(req));
    }

    @PostMapping("/requests/{requestId}/accept")
    public Result<FriendRequestDTO> accept(@RequestHeader("X-User-Id") long userId,
                                            @PathVariable("requestId") long requestId) {
        return Result.ok(friendService.acceptFriendRequest(new AcceptFriendRequestReq(requestId, userId)));
    }

    @PostMapping("/requests/{requestId}/reject")
    public Result<FriendRequestDTO> reject(@RequestHeader("X-User-Id") long userId,
                                            @PathVariable("requestId") long requestId) {
        return Result.ok(friendService.rejectFriendRequest(new RejectFriendRequestReq(requestId, userId)));
    }

    @DeleteMapping("/requests/{requestId}")
    public Result<Void> cancel(@RequestHeader("X-User-Id") long userId,
                                @PathVariable("requestId") long requestId) {
        friendService.cancelFriendRequest(new CancelFriendRequestReq(requestId, userId));
        return Result.ok();
    }

    // ==================== 好友管理 ====================

    @GetMapping
    public Result<ListFriendsResp> list(@RequestHeader("X-User-Id") long userId,
                                         @RequestParam(value = "groupId", required = false) Long groupId,
                                         @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "100") int pageSize) {
        ListFriendsReq req = new ListFriendsReq(userId, groupId, pageNum, pageSize);
        return Result.ok(friendService.listFriends(req));
    }

    @DeleteMapping("/{friendId}")
    public Result<Void> removeFriend(@RequestHeader("X-User-Id") long userId,
                                      @PathVariable("friendId") long friendId) {
        friendService.deleteFriend(new DeleteFriendReq(userId, friendId));
        return Result.ok();
    }

    @PutMapping("/{friendId}/remark")
    public Result<Void> setRemark(@RequestHeader("X-User-Id") long userId,
                                   @PathVariable("friendId") long friendId,
                                   @RequestBody RemarkBody body) {
        friendService.setRemark(new SetRemarkReq(userId, friendId, body.getRemark()));
        return Result.ok();
    }

    @PutMapping("/{friendId}/group")
    public Result<Void> moveGroup(@RequestHeader("X-User-Id") long userId,
                                   @PathVariable("friendId") long friendId,
                                   @RequestBody GroupMoveBody body) {
        friendService.moveGroup(new MoveGroupReq(userId, friendId, body.getGroupId()));
        return Result.ok();
    }

    // ==================== 好友分组 ====================

    @GetMapping("/groups")
    public Result<ListGroupsResp> listGroups(@RequestHeader("X-User-Id") long userId) {
        return Result.ok(friendService.listGroups(new ListGroupsReq(userId)));
    }

    @PostMapping("/groups")
    public Result<CreateGroupResp> createGroup(@RequestHeader("X-User-Id") long userId,
                                                @RequestBody NameBody body) {
        CreateGroupReq req = new CreateGroupReq(userId, body.getName(), 0);
        return Result.ok(friendService.createGroup(req));
    }

    @PutMapping("/groups/{groupId}")
    public Result<RenameGroupResp> renameGroup(@RequestHeader("X-User-Id") long userId,
                                                @PathVariable("groupId") long groupId,
                                                @RequestBody NameBody body) {
        RenameGroupReq req = new RenameGroupReq(userId, groupId, body.getName());
        return Result.ok(friendService.renameGroup(req));
    }

    @DeleteMapping("/groups/{groupId}")
    public Result<Void> deleteGroup(@RequestHeader("X-User-Id") long userId,
                                     @PathVariable("groupId") long groupId) {
        friendService.deleteGroup(new DeleteGroupReq(userId, groupId));
        return Result.ok();
    }

    // ==================== 黑名单 ====================

    @GetMapping("/blacklist")
    public Result<ListBlacklistResp> blacklist(@RequestHeader("X-User-Id") long userId,
                                                @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "100") int pageSize) {
        ListBlacklistReq req = new ListBlacklistReq(userId, pageNum, pageSize);
        return Result.ok(friendService.listBlacklist(req));
    }

    @PostMapping("/blacklist/{userId}")
    public Result<Void> block(@RequestHeader("X-User-Id") long operatorId,
                               @PathVariable("userId") long targetUserId) {
        friendService.blockUser(new BlockUserReq(operatorId, targetUserId));
        return Result.ok();
    }

    @DeleteMapping("/blacklist/{userId}")
    public Result<Void> unblock(@RequestHeader("X-User-Id") long operatorId,
                                 @PathVariable("userId") long targetUserId) {
        friendService.unblockUser(new UnblockUserReq(operatorId, targetUserId));
        return Result.ok();
    }

    // ==================== 请求 Body 内部类（与 api-v1.md 字段名对齐） ====================

    /** 发申请请求体：{ "toUserId": 123, "message": "hi" } */
    public static class SendBody {
        private long toUserId;
        private String message;
        public long getToUserId() { return toUserId; }
        public void setToUserId(long toUserId) { this.toUserId = toUserId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /** 分组名请求体：{ "name": "..." } */
    public static class NameBody {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** 备注请求体：{ "remark": "..." } */
    public static class RemarkBody {
        private String remark;
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /** 移动分组请求体：{ "groupId": 123 } */
    public static class GroupMoveBody {
        private long groupId;
        public long getGroupId() { return groupId; }
        public void setGroupId(long groupId) { this.groupId = groupId; }
    }
}
