package lanshan.manmu.user.controller;

import jakarta.validation.Valid;
import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.user.LoginReq;
import lanshan.manmu.common.rpc.dto.user.LoginResp;
import lanshan.manmu.common.rpc.dto.user.RegisterReq;
import lanshan.manmu.common.rpc.dto.user.RegisterResp;
import lanshan.manmu.common.rpc.dto.user.ValidateTokenResp;
import lanshan.manmu.user.dto.LoginRequest;
import lanshan.manmu.user.dto.LogoutRequest;
import lanshan.manmu.user.dto.RefreshTokenRequest;
import lanshan.manmu.user.dto.RefreshTokenResponse;
import lanshan.manmu.user.dto.RegisterRequest;
import lanshan.manmu.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller（spec controller-spec.md §5.1）。
 * <p>路径对齐 api-v1.md §2：{@code /api/v1/auth/**}。
 * <p>register/login/refresh 走网关白名单，不需 X-User-Id；logout/validate 需鉴权。
 *
 * <p>校验体系：common 模块的请求 DTO 为共享类型，不引入 jakarta.validation 依赖（保持 common 轻量），
 * 故 Controller 层使用 user-service 本地带 Bean Validation 注解的请求体（{@code @Valid}），
 * 校验通过后转换为 common DTO 调用 service 层。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<RegisterResp> register(@Valid @RequestBody RegisterRequest req) {
        RegisterReq commonReq = new RegisterReq(
                req.username(), req.password(), req.phone(), req.email(), req.deviceId(), req.platform());
        return Result.ok(userService.register(commonReq));
    }

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginRequest req) {
        LoginReq commonReq = new LoginReq(
                req.account(), req.password(), req.deviceId(), req.platform());
        return Result.ok(userService.login(commonReq));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader,
                                @Valid @RequestBody LogoutRequest req) {
        // 从 Authorization: Bearer <token> 提取 accessToken
        String accessToken = extractBearer(authHeader);
        userService.logout(accessToken, req.refreshToken());
        return Result.ok();
    }

    @GetMapping("/validate")
    public Result<ValidateTokenResp> validate(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractBearer(authHeader);
        return Result.ok(userService.validateToken(accessToken));
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        // refresh 时轮换：返回新的 accessToken + 新的 refreshToken，旧 refreshToken 一次性吊销
        return Result.ok(userService.refreshToken(req.refreshToken()));
    }

    private String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}
