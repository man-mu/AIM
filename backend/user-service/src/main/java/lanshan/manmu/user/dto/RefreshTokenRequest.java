package lanshan.manmu.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh Token 刷新请求（user-service 本地校验 DTO）。
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}
