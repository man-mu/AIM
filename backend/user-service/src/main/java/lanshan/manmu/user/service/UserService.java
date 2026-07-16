package lanshan.manmu.user.service;

import java.util.List;
import lanshan.manmu.common.rpc.dto.user.*;

/**
 * 用户服务接口。
 */
public interface UserService {

    RegisterResp register(RegisterReq req);
    LoginResp login(LoginReq req);
    void logout(long userId, String tokenId);
    ValidateTokenResp validateToken(String accessToken);
    RefreshTokenResp refreshToken(String refreshToken);

    UserInfo getUserInfo(long userId);
    BatchGetUserInfoResp batchGetUserInfo(List<Long> userIds);
    UserInfo updateProfile(long userId, UpdateProfileReq req);
    void updatePassword(long userId, String oldPwd, String newPwd);
    SearchUsersResp searchUsers(String keyword, int pageNum, int pageSize);
    List<Long> listAllUserIds();
}
