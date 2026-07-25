package lanshan.manmu.user.controller;

import lanshan.manmu.common.result.Result;
import lanshan.manmu.common.rpc.dto.user.LoginReq;
import lanshan.manmu.common.rpc.dto.user.LoginResp;
import lanshan.manmu.common.rpc.dto.user.LogoutReq;
import lanshan.manmu.common.rpc.dto.user.RefreshTokenReq;
import lanshan.manmu.common.rpc.dto.user.RefreshTokenResp;
import lanshan.manmu.common.rpc.dto.user.RegisterReq;
import lanshan.manmu.common.rpc.dto.user.RegisterResp;
import lanshan.manmu.common.rpc.dto.user.ValidateTokenResp;
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
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<RegisterResp> register(@RequestBody RegisterReq req) {
        return Result.ok(userService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginResp> login(@RequestBody LoginReq req) {
        return Result.ok(userService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader,
                                @RequestBody LogoutReq req) {
        // 从 Authorization: Bearer <token> 提取 accessToken
        String accessToken = extractBearer(authHeader);
        userService.logout(accessToken, req.getRefreshToken());
        return Result.ok();
    }

    @GetMapping("/validate")
    public Result<ValidateTokenResp> validate(@RequestHeader("Authorization") String authHeader) {
        String accessToken = extractBearer(authHeader);
        return Result.ok(userService.validateToken(accessToken));
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenResp> refresh(@RequestBody RefreshTokenReq req) {
        // refreshToken 接口仅返回新的 accessToken（refreshToken 不变）
        return Result.ok(userService.refreshToken(req.getRefreshToken()));
    }

    private String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}
