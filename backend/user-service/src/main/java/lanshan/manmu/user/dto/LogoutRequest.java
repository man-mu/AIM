package lanshan.manmu.user.dto;

/**
 * 登出请求（user-service 本地校验 DTO）。
 *
 * <p>两个字段均可为空（登出时 accessToken 从 Authorization header 提取，refreshToken 由 body 传入；
 * 任一缺失不影响另一方的吊销），故不做 @NotBlank 校验。
 */
public record LogoutRequest(
        String accessToken,
        String refreshToken
) {
}
