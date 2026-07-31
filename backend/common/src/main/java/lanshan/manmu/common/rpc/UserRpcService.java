package lanshan.manmu.common.rpc;

import lanshan.manmu.common.rpc.dto.user.*;

/**
 * 用户服务 Dubbo 接口。
 */
public interface UserRpcService {

    // —— 认证 ——
    RegisterResp register(RegisterReq req);
    LoginResp login(LoginReq req);
    void logout(LogoutReq req);
    ValidateTokenResp validateToken(ValidateTokenReq req);
    RefreshTokenResp refreshToken(RefreshTokenReq req);

    // —— 资料 ——
    /**
     * 查询指定用户资料。
     * <p>{@code viewerId} 为调用者（查看者）用户 id，用于隐私脱敏判定：当 {@code viewerId != req.userId}
     * 时返回的 {@code phone/email} 脱敏、{@code balance} 置 0；本人查询返回完整资料。
     * <p>跨服务调用（如未来会话展示用户资料页）<b>必须</b>传入调用者的 user id，避免泄露他人手机号/邮箱/余额。
     *
     * @param req      目标用户 id
     * @param viewerId 调用者用户 id（<=0 视为未知调用者，一律脱敏）
     */
    UserInfo getUserInfo(GetUserInfoReq req, long viewerId);

    /**
     * 批量查询用户资料。<b>默认全量脱敏</b>：所有返回条目的 {@code phone/email} 打码、{@code balance} 置 0。
     * <p>该方法仅用于会话成员补全 {@code username/avatar} 等非敏感字段，调用方无需敏感信息，故按默认安全策略脱敏。
     */
    BatchGetUserInfoResp batchGetUserInfo(BatchGetUserInfoReq req);
    UserInfo updateProfile(UpdateProfileReq req);
    void updatePassword(UpdatePasswordReq req);
    SearchUsersResp searchUsers(SearchUsersReq req);
    ListAllUserIdsResp listAllUserIds();
}
