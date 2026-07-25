package lanshan.manmu.user.controller;

import java.util.List;
import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp;
import lanshan.manmu.common.rpc.dto.user.SearchUsersResp;
import lanshan.manmu.common.rpc.dto.user.UpdatePasswordReq;
import lanshan.manmu.common.rpc.dto.user.UpdateProfileReq;
import lanshan.manmu.common.rpc.dto.user.UserInfo;
import lanshan.manmu.user.service.UserService;
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
 * 用户资料 Controller（spec controller-spec.md §5.1）。
 * <p>路径对齐 api-v1.md §3：{@code /api/v1/users/**}。
 * <p>所有接口都需鉴权，从网关注入的 {@code X-User-Id} header 取当前用户。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserInfo> me(@RequestHeader("X-User-Id") long userId) {
        return Result.ok(userService.getUserInfo(userId));
    }

    @PutMapping("/me")
    public Result<UserInfo> updateMe(@RequestHeader("X-User-Id") long userId,
                                      @RequestBody UpdateProfileReq req) {
        req.setUserId(userId);
        return Result.ok(userService.updateProfile(userId, req));
    }

    @PutMapping("/me/password")
    public Result<Void> updatePassword(@RequestHeader("X-User-Id") long userId,
                                        @RequestBody UpdatePasswordReq req) {
        req.setUserId(userId);
        userService.updatePassword(userId, req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }

    @GetMapping("/{userId}")
    public Result<UserInfo> getUser(@PathVariable("userId") long userId) {
        return Result.ok(userService.getUserInfo(userId));
    }

    @PostMapping("/batch")
    public Result<BatchGetUserInfoResp> batchGet(@RequestBody List<Long> userIds) {
        return Result.ok(userService.batchGetUserInfo(userIds));
    }

    @PostMapping("/search")
    public Result<SearchUsersResp> search(@RequestParam("keyword") String keyword,
                                           @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                           @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(userService.searchUsers(keyword, pageNum, pageSize));
    }
}
