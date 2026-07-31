package lanshan.manmu.user.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.user.*;
import lanshan.manmu.user.dto.RefreshTokenResponse;

/**
 * 用户服务接口。
 */
public interface UserService {

    RegisterResp register(RegisterReq req);
    LoginResp login(LoginReq req);
    void logout(String accessToken, String refreshToken);
    ValidateTokenResp validateToken(String accessToken);

    /**
     * 刷新 token：校验 refreshToken 合法性后签发新 accessToken，并轮换 refreshToken（旧 refreshToken 吊销）。
     *
     * @param refreshToken 旧的 refreshToken
     * @return 含新 accessToken + 新 refreshToken 的响应（user-service 本地类型，支持轮换）
     */
    RefreshTokenResponse refreshToken(String refreshToken);

    /**
     * 查询用户资料。
     *
     * @param userId   目标用户 id
     * @param viewerId 调用者（查看者）用户 id；等于 userId 时返回完整资料，否则 phone/email 脱敏、balance 置 0
     */
    UserInfo getUserInfo(long userId, long viewerId);
    /**
     * 批量查询用户资料：默认对每条结果脱敏（phone/email 打码、balance 置 0）。
     */
    BatchGetUserInfoResp batchGetUserInfo(List<Long> userIds);
    UserInfo updateProfile(long userId, UpdateProfileReq req);
    void updatePassword(long userId, String oldPwd, String newPwd);
    SearchUsersResp searchUsers(String keyword, int pageNum, int pageSize);
    List<Long> listAllUserIds();
}
