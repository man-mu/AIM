package lanshan.manmu.user.service.impl;

import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务核心实现：BCrypt 密码哈希 + JWT 签发 + Redis 黑名单 + MyBatis-Plus CRUD。
 * <p>JWT 校验流程：Hutool {@code JWT.of(token).setKey(secret).verify()} 签名校验 + exp/nbf/iat 过期校验
 * + Redis 黑名单（{@code revoked_token:{jti}}）吊销校验。
 * <p>注册唯一性：应用层查重快速失败 + DB 部分唯一索引兜底（见 {@code aim-schema.sql}）。
 */
@Service
@Slf4j
@RefreshScope
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserDeviceMapper deviceMapper;
    private final SnowflakeIdWorker snowflake;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final byte[] jwtSecretBytes;
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
        this.jwtSecretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.jwtExpireSec = jwtExpireSec;
        this.jwtRefreshSec = jwtRefreshSec;
    }

    // ==================== 认证 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResp register(RegisterReq req) {
        // 1. 参数校验
        if (req.getUsername() == null || req.getUsername().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "username 不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "password 不能为空");
        }
        validatePasswordStrength(req.getPassword());

        // 2. 唯一性检查（单次 OR 查询，减少 RTT；并发竞态由 DB 部分唯一索引兜底）
        boolean hasPhone = req.getPhone() != null && !req.getPhone().isEmpty();
        boolean hasEmail = req.getEmail() != null && !req.getEmail().isEmpty();
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getUsername, req.getUsername());
        if (hasPhone) {
            qw.or().eq(User::getPhone, req.getPhone());
        }
        if (hasEmail) {
            qw.or().eq(User::getEmail, req.getEmail());
        }
        List<User> existing = userMapper.selectList(qw);
        if (!existing.isEmpty()) {
            // 单次遍历逐字段匹配，优先级：username > phone > email
            for (User conflict : existing) {
                if (conflict.getUsername().equals(req.getUsername())) {
                    log.warn("register 用户名已存在: username={}", req.getUsername());
                    throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
                }
                if (hasPhone && req.getPhone().equals(conflict.getPhone())) {
                    log.warn("register 手机号已注册: phone={}", maskPhone(req.getPhone()));
                    throw new BizException(ErrorCode.USER_PHONE_EXISTS);
                }
                if (hasEmail && req.getEmail().equals(conflict.getEmail())) {
                    log.warn("register 邮箱已注册: email={}", maskEmail(req.getEmail()));
                    throw new BizException(ErrorCode.USER_EMAIL_EXISTS);
                }
            }
            // 兜底（理论不可达，OR 查询的每条匹配必命中上述三个字段之一）
            log.warn("register OR 查询命中但未识别冲突字段: username={}", req.getUsername());
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 3. Snowflake ID
        long userId = snowflake.nextId();

        // 4. BCrypt 哈希
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 5. 构建实体
        OffsetDateTime now = OffsetDateTime.now();
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

        // 6. 持久化（并发竞态下由 DB 唯一索引抛 DuplicateKeyException）
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("register 竞态唯一键冲突: userId={}, username={}, msg={}", userId, req.getUsername(), e.getMessage());
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 7. JWT 双 Token
        TokenPair tokens = generateTokenPair(userId, req.getUsername());

        // 8. 设备记录
        if (req.getDeviceId() != null && !req.getDeviceId().isEmpty()) {
            saveDevice(userId, req.getDeviceId(), req.getPlatform());
        }

        // 9. 响应
        log.info("register 成功: userId={}, username={}", userId, req.getUsername());
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
            log.warn("login 用户不存在: account={}", req.getAccount());
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 密码校验
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("login 密码错误: userId={}", user.getId());
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // JWT 双 Token
        TokenPair tokens = generateTokenPair(user.getId(), user.getUsername());

        // 设备记录
        if (req.getDeviceId() != null && !req.getDeviceId().isEmpty()) {
            saveDevice(user.getId(), req.getDeviceId(), req.getPlatform());
        }

        log.info("login 成功: userId={}", user.getId());
        LoginResp resp = new LoginResp();
        resp.setUserId(user.getId());
        resp.setTokens(tokens);
        resp.setUser(toUserInfo(user));
        return resp;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        boolean accessRevoked = revokeIfValid(accessToken, "access");
        boolean refreshRevoked = revokeIfValid(refreshToken, "refresh");
        if (!accessRevoked && !refreshRevoked) {
            log.info("logout 未吊销任何有效 token");
        }
    }

    /**
     * 若 token 有效且未过期，将其 jti 加入 Redis 黑名单。
     * @return true 表示已加入黑名单；false 表示 token 无效/过期/缺 jti
     */
    private boolean revokeIfValid(String token, String kind) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        JWT jwt = parseAndVerify(token);
        if (jwt == null) {
            log.info("logout 忽略无效 {} token", kind);
            return false;
        }
        Object jtiObj = jwt.getPayload("jti");
        String jti = jtiObj == null ? null : String.valueOf(jtiObj);
        if (jti == null || jti.isEmpty() || "null".equals(jti)) {
            log.info("logout {} token 缺少 jti", kind);
            return false;
        }
        long expiresAt = extractExpiration(jwt);
        long now = System.currentTimeMillis();
        long ttl = (expiresAt - now) / 1000;
        if (ttl <= 0) {
            return false;
        }
        redisTemplate.opsForValue().set("revoked_token:" + jti, "1", Duration.ofSeconds(ttl));
        log.info("logout 已吊销 {} token jti={}", kind, jti);
        return true;
    }

    @Override
    public RefreshTokenResp refreshToken(String refreshToken) {
        JWT jwt = parseAndVerify(refreshToken);
        if (jwt == null) {
            log.warn("refreshToken 验签或过期校验失败");
            throw new BizException(ErrorCode.USER_TOKEN_INVALID);
        }

        // 必须 type=refresh，防止 accessToken 被当 refreshToken 用
        Object typeObj = jwt.getPayload("type");
        String type = typeObj == null ? null : String.valueOf(typeObj);
        if (!"refresh".equals(type)) {
            log.warn("refreshToken 类型错误: type={}", type);
            throw new BizException(ErrorCode.USER_TOKEN_INVALID);
        }

        // Redis 黑名单校验
        Object jtiObj = jwt.getPayload("jti");
        String jti = jtiObj == null ? null : String.valueOf(jtiObj);
        if (jti == null || jti.isEmpty() || "null".equals(jti)
                || redisTemplate.hasKey("revoked_token:" + jti)) {
            log.warn("refreshToken 已被吊销或缺 jti");
            throw new BizException(ErrorCode.USER_TOKEN_INVALID);
        }

        // 签发新 accessToken（refreshToken 不变，长效复用）
        try {
            long userId = Long.parseLong(String.valueOf(jwt.getPayload("userId")));
            String username = String.valueOf(jwt.getPayload("username"));
            String accessToken = generateAccessToken(userId, username);

            log.info("refreshToken 成功: userId={}", userId);
            return new RefreshTokenResp(accessToken, System.currentTimeMillis() + jwtExpireSec * 1000);
        } catch (Exception e) {
            log.warn("refreshToken 解析 userId/username 失败: {}", e.getMessage());
            throw new BizException(ErrorCode.USER_TOKEN_INVALID);
        }
    }

    @Override
    public ValidateTokenResp validateToken(String accessToken) {
        JWT jwt = parseAndVerify(accessToken);
        if (jwt == null) {
            return invalidTokenResp();
        }

        // 类型校验：只接受 accessToken，防止 refreshToken 被当 accessToken 滥用
        Object typeObj = jwt.getPayload("type");
        String type = typeObj == null ? null : String.valueOf(typeObj);
        if (!"access".equals(type)) {
            log.warn("validateToken 类型错误: type={}", type);
            return invalidTokenResp();
        }

        // Redis 黑名单校验
        Object jtiObj = jwt.getPayload("jti");
        String jti = jtiObj == null ? null : String.valueOf(jtiObj);
        if (jti == null || jti.isEmpty() || "null".equals(jti)) {
            return invalidTokenResp();
        }
        if (redisTemplate.hasKey("revoked_token:" + jti)) {
            return invalidTokenResp();
        }

        try {
            long userId = Long.parseLong(String.valueOf(jwt.getPayload("userId")));
            long expiresAt = extractExpiration(jwt);

            ValidateTokenResp resp = new ValidateTokenResp();
            resp.setValid(true);
            resp.setUserId(userId);
            resp.setExpiresAt(expiresAt);
            return resp;
        } catch (Exception e) {
            log.warn("validateToken 解析 userId 失败: {}", e.getMessage());
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
        if (userIds == null || userIds.isEmpty()) {
            BatchGetUserInfoResp resp = new BatchGetUserInfoResp();
            resp.setUsers(List.of());
            return resp;
        }
        if (userIds.size() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单次最多查询 500 个用户");
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        List<UserInfo> infos = users.stream().map(this::toUserInfo).toList();
        BatchGetUserInfoResp resp = new BatchGetUserInfoResp();
        resp.setUsers(infos);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfo updateProfile(long userId, UpdateProfileReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 字段值校验
        if (req.getGender() != null && (req.getGender() < 0 || req.getGender() > 2)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "gender 取值需 0/1/2");
        }
        if (req.getBirthday() != null && req.getBirthday() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "birthday 不能为负数");
        }
        if (req.getBio() != null && req.getBio().length() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "bio 长度不能超过 500");
        }
        if (req.getAvatar() != null && req.getAvatar().length() > 512) {
            throw new BizException(ErrorCode.BAD_REQUEST, "avatar 长度不能超过 512");
        }

        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        if (req.getAvatar() != null) wrapper.set("avatar", req.getAvatar());
        if (req.getGender() != null) wrapper.set("gender", req.getGender());
        if (req.getBio() != null) wrapper.set("bio", req.getBio());
        if (req.getBirthday() != null) wrapper.set("birthday", req.getBirthday());

        // phone/email：传入 null 跳过更新；传入非空值且与当前值不同则查重+更新；传入空串=清空
        if (req.getPhone() != null && !req.getPhone().equals(user.getPhone())) {
            if (!req.getPhone().isEmpty()) {
                Long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, req.getPhone()));
                if (cnt != null && cnt > 0) {
                    log.warn("updateProfile 手机号已被占用: userId={}, phone={}", userId, maskPhone(req.getPhone()));
                    throw new BizException(ErrorCode.USER_PHONE_EXISTS);
                }
            }
            wrapper.set("phone", req.getPhone());
        }
        if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            if (!req.getEmail().isEmpty()) {
                Long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, req.getEmail()));
                if (cnt != null && cnt > 0) {
                    log.warn("updateProfile 邮箱已被占用: userId={}, email={}", userId, maskEmail(req.getEmail()));
                    throw new BizException(ErrorCode.USER_EMAIL_EXISTS);
                }
            }
            wrapper.set("email", req.getEmail());
        }

        wrapper.set("updated_at", OffsetDateTime.now());
        try {
            userMapper.update(null, wrapper);
        } catch (DuplicateKeyException e) {
            // 应用层查重漏网（并发竞态），DB 唯一索引兜底
            log.warn("updateProfile DB 唯一键冲突: userId={}", userId);
            Long phoneCnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, req.getPhone()).ne(User::getId, userId));
            if (phoneCnt != null && phoneCnt > 0) {
                throw new BizException(ErrorCode.USER_PHONE_EXISTS);
            }
            throw new BizException(ErrorCode.USER_EMAIL_EXISTS);
        }

        return getUserInfo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(long userId, String oldPwd, String newPwd) {
        validatePasswordStrength(newPwd);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPwd, user.getPasswordHash())) {
            log.warn("updatePassword 旧密码错误: userId={}", userId);
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .set("password_hash", passwordEncoder.encode(newPwd))
                .set("updated_at", OffsetDateTime.now());
        userMapper.update(null, wrapper);
    }

    @Override
    public SearchUsersResp searchUsers(String keyword, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 10;
        if (pageSize > 100) pageSize = 100;
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(User::getUsername, escapeLike(keyword))
                .orderByAsc(User::getId);
        userMapper.selectPage(page, wrapper);

        // 隐私保护：搜索他人时 phone/email 脱敏，避免泄露他人手机号邮箱
        List<UserInfo> users = page.getRecords().stream().map(u -> {
            UserInfo info = toUserInfo(u);
            info.setPhone(maskPhone(info.getPhone()));
            info.setEmail(maskEmail(info.getEmail()));
            return info;
        }).toList();
        SearchUsersResp resp = new SearchUsersResp();
        resp.setUsers(users);
        resp.setTotal(page.getTotal());
        return resp;
    }

    @Override
    // TODO: 待 signaling-service 接入时改为游标分页或 Redis 缓存，百万级数据会有 OOM 风险
    public List<Long> listAllUserIds() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId));
        return users.stream().map(User::getId).toList();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 转义 LIKE 通配符（\、%、_），防止搜索词被当作通配符误匹配。
     * PG 9.1+ 默认 LIKE 反斜杠转义生效。
     */
    private String escapeLike(String keyword) {
        if (keyword == null) return "";
        return keyword.replace("\\", "\\\\")
                       .replace("%", "\\%")
                       .replace("_", "\\_");
    }

    /** 手机号脱敏：保留前 3 后 4，中间 4 位打码。空串原样返回。 */
    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return phone;
        if (phone.length() <= 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 邮箱脱敏：@ 前保留前 1 字符，其余打码。空串原样返回。 */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "****" + email.substring(at);
    }

    /**
     * 生成 JWT 双 Token（Access + Refresh）。
     */
    private TokenPair generateTokenPair(long userId, String username) {
        long now = System.currentTimeMillis();
        long accessExpireAt = now + jwtExpireSec * 1000;
        long refreshExpireAt = now + jwtRefreshSec * 1000;

        String accessToken = generateAccessToken(userId, username, now, accessExpireAt);

        String refreshToken = JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("type", "refresh")
                .setIssuer("aim")
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(refreshExpireAt))
                .setKey(jwtSecretBytes)
                .sign();

        TokenPair pair = new TokenPair();
        pair.setAccessToken(accessToken);
        pair.setRefreshToken(refreshToken);
        pair.setAccessExpire(accessExpireAt);
        pair.setRefreshExpire(refreshExpireAt);
        return pair;
    }

    /**
     * 生成 accessToken（用于 refresh 时单发新 accessToken）。
     * 默认签发时刻取当前时间，过期时间 = now + jwtExpireSec。
     */
    private String generateAccessToken(long userId, String username) {
        long now = System.currentTimeMillis();
        return generateAccessToken(userId, username, now, now + jwtExpireSec * 1000);
    }

    private String generateAccessToken(long userId, String username, long now, long expireAt) {
        return JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setPayload("type", "access")
                .setIssuer("aim")
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(expireAt))
                .setKey(jwtSecretBytes)
                .sign();
    }

    /**
     * 保存设备记录（upsert 语义）。
     */
    private void saveDevice(long userId, String deviceId, String platform) {
        UserDevice existing = deviceMapper.selectOne(
                new LambdaQueryWrapper<UserDevice>()
                        .eq(UserDevice::getUserId, userId)
                        .eq(UserDevice::getDeviceId, deviceId));
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null) {
            existing.setLastActiveAt(now);
            if (platform != null) existing.setPlatform(platform);
            deviceMapper.updateById(existing);
            return;
        }
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
        try {
            deviceMapper.insert(device);
        } catch (DuplicateKeyException e) {
            // 并发竞态：两个请求同时 select 返回 null，第二个 insert 撞 (user_id, device_id) 唯一索引
            log.info("saveDevice 竞态冲突,降级为 update: userId={}, deviceId={}", userId, deviceId);
            UserDevice conflict = deviceMapper.selectOne(
                    new LambdaQueryWrapper<UserDevice>()
                            .eq(UserDevice::getUserId, userId)
                            .eq(UserDevice::getDeviceId, deviceId));
            if (conflict != null) {
                conflict.setLastActiveAt(now);
                if (platform != null) conflict.setPlatform(platform);
                deviceMapper.updateById(conflict);
            }
        }
    }

    /**
     * 密码强度校验：长度 6~32，必须同时含字母和数字。
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码长度需 6~32 位");
        }
        boolean hasLetter = false, hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码必须同时含字母和数字");
        }
    }

    /**
     * 解析并校验 JWT：① 签名校验 ② 过期校验。失败一律返回 null。
     */
    private JWT parseAndVerify(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            JWT jwt = JWT.of(token).setKey(jwtSecretBytes);
            if (!jwt.verify()) {
                return null;
            }
            jwt.validate(0);
            return jwt;
        } catch (Exception e) {
            return null;
        }
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
            long v = n.longValue();
            // JWT 规范中 exp 是秒级时间戳；若值小于 10^12 判定为秒级，乘 1000 转毫秒
            return v < 1_000_000_000_000L ? v * 1000L : v;
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
        info.setBalance(user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO);
        return info;
    }

    /**
     * OffsetDateTime → epoch millis。
     */
    private long toEpochMillis(OffsetDateTime odt) {
        if (odt == null) return 0L;
        return odt.toInstant().toEpochMilli();
    }
}