package lanshan.manmu.user.controller;

import jakarta.validation.Valid;
import java.util.List;
import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp;
import lanshan.manmu.common.rpc.dto.user.SearchUsersResp;
import lanshan.manmu.common.rpc.dto.user.UpdateProfileReq;
import lanshan.manmu.common.rpc.dto.user.UserInfo;
import lanshan.manmu.user.dto.UpdatePasswordRequest;
import lanshan.manmu.user.dto.UpdateProfileRequest;
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
 *
 * <p>校验体系：common 模块的请求 DTO 为共享类型，不引入 jakarta.validation 依赖（保持 common 轻量），
 * 故写接口使用 user-service 本地带 Bean Validation 注解的请求体（{@code @Valid}），
 * 校验通过后转换为 common DTO 调用 service 层。{@code X-User-Id}/{@code userId} 参数绑定失败
 * （header 缺失或路径变量非数字）由 GlobalExceptionHandler 统一映射为 4xx。
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
        // 本人：返回完整资料
        return Result.ok(userService.getUserInfo(userId, userId));
    }

    @PutMapping("/me")
    public Result<UserInfo> updateMe(@RequestHeader("X-User-Id") long userId,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        UpdateProfileReq commonReq = new UpdateProfileReq();
        commonReq.setUserId(userId);
        commonReq.setAvatar(req.avatar());
        commonReq.setGender(req.gender());
        commonReq.setBio(req.bio());
        commonReq.setBirthday(req.birthday());
        commonReq.setPhone(req.phone());
        commonReq.setEmail(req.email());
        return Result.ok(userService.updateProfile(userId, commonReq));
    }

    @PutMapping("/me/password")
    public Result<Void> updatePassword(@RequestHeader("X-User-Id") long userId,
                                        @Valid @RequestBody UpdatePasswordRequest req) {
        userService.updatePassword(userId, req.oldPassword(), req.newPassword());
        return Result.ok();
    }

    @GetMapping("/{userId}")
    public Result<UserInfo> getUser(@PathVariable("userId") long userId,
                                     @RequestHeader("X-User-Id") long viewerId) {
        // viewerId 来自网关注入（鉴权后由 JWT 解出），用于脱敏判定：本人返回完整资料，
        // 他人 phone/email 脱敏、balance 置 0
        return Result.ok(userService.getUserInfo(userId, viewerId));
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
