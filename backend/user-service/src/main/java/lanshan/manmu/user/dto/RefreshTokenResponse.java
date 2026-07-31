package lanshan.manmu.user.dto;

/**
 * Refresh Token 刷新响应（user-service 本地响应 DTO，支持 refreshToken 轮换）。
 *
 * <p>common 模块的 {@code RefreshTokenResp} 仅含 accessToken + accessExpire（不支持轮换新 refreshToken），
 * 为实现「refresh 时轮换 refreshToken」且不修改 common 共享 DTO，user-service 内定义本响应体，
 * 由 HTTP Controller 直接返回。Dubbo RPC 路径（{@code UserRpcService.refreshToken}）受 common 契约限制，
 * 仍返回 common {@code RefreshTokenResp}（仅 accessToken）。
 *
 * @param accessToken   新签发的 accessToken
 * @param refreshToken  新签发的 refreshToken（轮换，旧 refreshToken 已吊销）
 * @param accessExpire  accessToken 过期时间（epoch millis）
 * @param refreshExpire refreshToken 过期时间（epoch millis）
 */
public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long accessExpire,
        long refreshExpire
) {
}
