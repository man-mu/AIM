package lanshan.manmu.user.rpc;

import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.user.*;
import lanshan.manmu.user.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 用户服务 Dubbo Provider 实现。
 */
@DubboService
public class UserRpcServiceImpl implements UserRpcService {

    @Autowired
    private UserService userService;

    @Override
    public RegisterResp register(RegisterReq req) {
        return userService.register(req);
    }

    @Override
    public LoginResp login(LoginReq req) {
        return userService.login(req);
    }

    @Override
    public void logout(LogoutReq req) {
        userService.logout(req.getUserId(), req.getTokenId());
    }

    @Override
    public ValidateTokenResp validateToken(ValidateTokenReq req) {
        return userService.validateToken(req.getAccessToken());
    }

    @Override
    public UserInfo getUserInfo(GetUserInfoReq req) {
        return userService.getUserInfo(req.getUserId());
    }

    @Override
    public BatchGetUserInfoResp batchGetUserInfo(BatchGetUserInfoReq req) {
        return userService.batchGetUserInfo(req.getUserIds());
    }

    @Override
    public UserInfo updateProfile(UpdateProfileReq req) {
        return userService.updateProfile(req.getUserId(), req);
    }

    @Override
    public SearchUsersResp searchUsers(SearchUsersReq req) {
        return userService.searchUsers(req.getKeyword(), req.getPageNum(), req.getPageSize());
    }

    @Override
    public ListAllUserIdsResp listAllUserIds() {
        ListAllUserIdsResp resp = new ListAllUserIdsResp();
        resp.setUserIds(userService.listAllUserIds());
        return resp;
    }
}
