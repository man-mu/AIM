package lanshan.manmu.user.service.impl;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.user.*;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.user.mapper.UserDeviceMapper;
import lanshan.manmu.user.mapper.UserMapper;
import lanshan.manmu.user.model.entity.User;
import lanshan.manmu.user.model.entity.UserDevice;
import lanshan.manmu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务核心实现：BCrypt 密码哈希 + JWT 签发 + Redis 会话管理 + MyBatis-Plus CRUD。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserDeviceMapper deviceMapper;
    private final SnowflakeIdWorker snowflake;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String jwtSecret;
    private final long jwtExpireSec;
    private final long jwtRefreshSec;

    public UserServiceImpl(
            UserMapper userMapper,
            UserDeviceMapper deviceMapper,
            SnowflakeIdWorker snowflake,
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expire-sec}") long jwtExpireSec,
            @Value("${jwt.refresh-sec}") long jwtRefreshSec) {
        this.userMapper = userMapper;
        this.deviceMapper = deviceMapper;
        this.snowflake = snowflake;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtSecret = jwtSecret;
        this.jwtExpireSec = jwtExpireSec;
        this.jwtRefreshSec = jwtRefreshSec;
    }

    // ==================== 认证 ====================

    @Override
    public RegisterResp register(RegisterReq req) {
        // 1. 参数校验
        if (req.getUsername() == null || req.getUsername().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "username 不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "password 不能为空");
        }

        // 2. 唯一性检查
        if (userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())) != null) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }
        if (req.getPhone() != null && !req.getPhone().isEmpty()) {
            if (userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, req.getPhone())) != null) {
                throw new BizException(ErrorCode.USER_PHONE_EXISTS);
            }
        }
        if (req.getEmail() != null && !req.getEmail().isEmpty()) {
            if (userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, req.getEmail())) != null) {
                throw new BizException(ErrorCode.USER_EMAIL_EXISTS);
            }
        }

        // 3. Snowflake ID
        long userId = snowflake.nextId();

        // 4. BCrypt 哈希
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 5. 构建实体
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(userId);
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordHash);
        user.setPhone(req.getPhone() != null ? req.getPhone() : "");
        user.setEmail(req.getEmail() != null ? req.getEmail() : "");
        user.setAvatar("");
        user.setGender(0);
        user.setBio("");
        user.setBirthday(0L);
        user.setBalance(BigDecimal.ZERO);
        user.setSettings("{}");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // 6. 持久化
        userMapper.insert(user);

        // 7. JWT 双 Token
        TokenPair tokens = generateTokenPair(userId, req.getUsername());

        // 8. 设备记录 + Redis 会话
        if (req.getDeviceId() != null && !req.getDeviceId().isEmpty()) {
            saveDevice(userId, req.getDeviceId(), req.getPlatform());
            saveSession(userId, req.getDeviceId(), tokens.getAccessToken());
        }

        // 9. 响应
        RegisterResp resp = new RegisterResp();
        resp.setUserId(userId);
        resp.setTokens(tokens);
        resp.setUser(toUserInfo(user));
        return resp;
    }

    @Override
    public LoginResp login(LoginReq req) {
        if (req.getAccount() == null || req.getPassword() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        // 三阶段查找：username → phone → email
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getAccount()));
        if (user == null) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, req.getAccount()));
        }
        if (user == null) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, req.getAccount()));
        }
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 密码校验
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // JWT 双 Token
        TokenPair tokens = generateTokenPair(user.getId(), user.getUsername());

        // 设备记录 + Redis 会话
        if (req.getDeviceId() != null && !req.getDeviceId().isEmpty()) {
            saveDevice(user.getId(), req.getDeviceId(), req.getPlatform());
            saveSession(user.getId(), req.getDeviceId(), tokens.getAccessToken());
        }

        LoginResp resp = new LoginResp();
        resp.setUserId(user.getId());
        resp.setTokens(tokens);
        resp.setUser(toUserInfo(user));
        return resp;
    }

    @Override
    public void logout(long userId, String tokenId) {
        try {
            JWT jwt = JWTUtil.parseToken(tokenId);
            jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8));

            String jti = String.valueOf(jwt.getPayload("jti"));
            long expiresAt = extractExpiration(jwt);

            long now = System.currentTimeMillis();
            if (expiresAt <= now) {
                return;
            }
            long ttl = (expiresAt - now) / 1000;
            redisTemplate.opsForValue().set("revoked_token:" + jti, "1", Duration.ofSeconds(ttl));
        } catch (Exception e) {
            // Token 无效，无需吊销
        }
    }

    @Override
    public ValidateTokenResp validateToken(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return invalidTokenResp();
        }
        try {
            JWT jwt = JWTUtil.parseToken(accessToken);
            jwt.setKey(jwtSecret.getBytes(StandardCharsets.UTF_8));

            // Redis 黑名单校验
            String jti = String.valueOf(jwt.getPayload("jti"));
            if (Boolean.TRUE.equals(redisTemplate.hasKey("revoked_token:" + jti))) {
                return invalidTokenResp();
            }

            long userId = Long.parseLong(String.valueOf(jwt.getPayload("userId")));
            long expiresAt = extractExpiration(jwt);

            ValidateTokenResp resp = new ValidateTokenResp();
            resp.setValid(true);
            resp.setUserId(userId);
            resp.setExpiresAt(expiresAt);
            return resp;
        } catch (Exception e) {
            return invalidTokenResp();
        }
    }

    // ==================== 资料 ====================

    @Override
    public UserInfo getUserInfo(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserInfo(user);
    }

    @Override
    public BatchGetUserInfoResp batchGetUserInfo(List<Long> userIds) {
        List<User> users = userMapper.selectBatchIds(userIds);
        List<UserInfo> infos = users.stream().map(this::toUserInfo).toList();
        BatchGetUserInfoResp resp = new BatchGetUserInfoResp();
        resp.setUsers(infos);
        return resp;
    }

    @Override
    public UserInfo updateProfile(long userId, UpdateProfileReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 密码变更
        if (req.getOldPassword() != null && req.getNewPassword() != null) {
            updatePassword(userId, req.getOldPassword(), req.getNewPassword());
        }

        // 逐字段更新非 null 值
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        if (req.getAvatar() != null) wrapper.set("avatar", req.getAvatar());
        if (req.getGender() != null) wrapper.set("gender", req.getGender());
        if (req.getBio() != null) wrapper.set("bio", req.getBio());
        if (req.getBirthday() != null) wrapper.set("birthday", req.getBirthday());
        if (req.getPhone() != null) wrapper.set("phone", req.getPhone());
        if (req.getEmail() != null) wrapper.set("email", req.getEmail());
        wrapper.set("updated_at", LocalDateTime.now());
        userMapper.update(null, wrapper);

        return getUserInfo(userId);
    }

    @Override
    public void updatePassword(long userId, String oldPwd, String newPwd) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPwd, user.getPasswordHash())) {
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .set("password_hash", passwordEncoder.encode(newPwd))
                .set("updated_at", LocalDateTime.now());
        userMapper.update(null, wrapper);
    }

    @Override
    public SearchUsersResp searchUsers(String keyword, int pageNum, int pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(User::getUsername, keyword);
        userMapper.selectPage(page, wrapper);

        List<UserInfo> users = page.getRecords().stream().map(this::toUserInfo).toList();
        SearchUsersResp resp = new SearchUsersResp();
        resp.setUsers(users);
        resp.setTotal(page.getTotal());
        return resp;
    }

    @Override
    public List<Long> listAllUserIds() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId));
        return users.stream().map(User::getId).toList();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 生成 JWT 双 Token（Access + Refresh）。
     */
    private TokenPair generateTokenPair(long userId, String username) {
        long now = System.currentTimeMillis();
        long accessExpireAt = now + jwtExpireSec * 1000;
        long refreshExpireAt = now + jwtRefreshSec * 1000;
        byte[] key = jwtSecret.getBytes(StandardCharsets.UTF_8);

        String accessToken = JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setIssuer("aim")
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(accessExpireAt))
                .setKey(key)
                .sign();

        String refreshToken = JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("type", "refresh")
                .setIssuer("aim")
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(refreshExpireAt))
                .setKey(key)
                .sign();

        TokenPair pair = new TokenPair();
        pair.setAccessToken(accessToken);
        pair.setRefreshToken(refreshToken);
        pair.setAccessExpire(accessExpireAt);
        pair.setRefreshExpire(refreshExpireAt);
        return pair;
    }

    /**
     * 保存设备记录（upsert 语义）。
     */
    private void saveDevice(long userId, String deviceId, String platform) {
        UserDevice existing = deviceMapper.selectOne(
                new LambdaQueryWrapper<UserDevice>()
                        .eq(UserDevice::getUserId, userId)
                        .eq(UserDevice::getDeviceId, deviceId));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setLastActiveAt(now);
            if (platform != null) existing.setPlatform(platform);
            deviceMapper.updateById(existing);
        } else {
            UserDevice device = new UserDevice();
            device.setId(snowflake.nextId());
            device.setUserId(userId);
            device.setDeviceId(deviceId);
            device.setPlatform(platform != null ? platform : "web");
            device.setPushToken("");
            device.setIp("");
            device.setLocation("");
            device.setLastActiveAt(now);
            device.setCreatedAt(now);
            deviceMapper.insert(device);
        }
    }

    /**
     * 保存 Redis 会话缓存。
     */
    private void saveSession(long userId, String deviceId, String accessToken) {
        String key = String.format("session:%d:%s", userId, deviceId);
        redisTemplate.opsForValue().set(key, accessToken, Duration.ofSeconds(jwtExpireSec));
    }

    /**
     * 从 JWT 提取过期时间（epoch millis）。
     */
    private long extractExpiration(JWT jwt) {
        Object exp = jwt.getPayload("exp");
        if (exp instanceof java.util.Date d) {
            return d.getTime();
        }
        if (exp instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    /**
     * 构造无效 Token 响应。
     */
    private ValidateTokenResp invalidTokenResp() {
        ValidateTokenResp resp = new ValidateTokenResp();
        resp.setValid(false);
        return resp;
    }

    /**
     * User 实体 → UserInfo DTO 转换。
     */
    private UserInfo toUserInfo(User user) {
        if (user == null) return null;
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setPhone(user.getPhone());
        info.setEmail(user.getEmail());
        info.setAvatar(user.getAvatar());
        info.setGender(user.getGender() != null ? user.getGender() : 0);
        info.setBio(user.getBio());
        info.setBirthday(user.getBirthday() != null ? user.getBirthday() : 0L);
        info.setCreatedAt(toEpochMillis(user.getCreatedAt()));
        info.setUpdatedAt(toEpochMillis(user.getUpdatedAt()));
        info.setBalance(user.getBalance() != null ? user.getBalance().doubleValue() : 0.0);
        return info;
    }

    /**
     * LocalDateTime → epoch millis。
     */
    private long toEpochMillis(LocalDateTime ldt) {
        if (ldt == null) return 0L;
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
