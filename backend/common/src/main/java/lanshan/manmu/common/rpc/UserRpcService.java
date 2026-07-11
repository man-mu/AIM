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

    // —— 资料 ——
    UserInfo getUserInfo(GetUserInfoReq req);
    BatchGetUserInfoResp batchGetUserInfo(BatchGetUserInfoReq req);
    UserInfo updateProfile(UpdateProfileReq req);
    SearchUsersResp searchUsers(SearchUsersReq req);
    ListAllUserIdsResp listAllUserIds();
}
