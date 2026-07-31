package lanshan.manmu.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.jwt.JWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.rpc.dto.user.LoginReq;
import lanshan.manmu.common.rpc.dto.user.LoginResp;
import lanshan.manmu.common.rpc.dto.user.RegisterReq;
import lanshan.manmu.common.rpc.dto.user.RegisterResp;
import lanshan.manmu.common.rpc.dto.user.ValidateTokenResp;
import lanshan.manmu.common.util.SnowflakeIdWorker;
import lanshan.manmu.user.dto.RefreshTokenResponse;
import lanshan.manmu.user.mapper.UserDeviceMapper;
import lanshan.manmu.user.mapper.UserMapper;
import lanshan.manmu.user.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link UserServiceImpl} 核心安全逻辑单元测试。
 *
 * <p>覆盖三个中等问题修复：
 * <ul>
 *   <li>login 防爆破：连续失败达阈值后锁定，用户不存在也计数，成功清零。</li>
 *   <li>refresh token 轮换：返回新 refreshToken 且旧 jti 进黑名单。</li>
 *   <li>改密后吊销：pwd_changed 时间戳使改密前签发的 token 失效（validateToken + refreshToken）。</li>
 * </ul>
 *
 * <p>纯 Mockito 单测，不启动 Spring 容器；用 Hutool JWT 与 UserServiceImpl 同库同算法自签 token。
 */
class UserServiceImplTest {

    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-test-only-0123456789";

    private UserMapper userMapper;
    private UserDeviceMapper deviceMapper;
    private SnowflakeIdWorker snowflake;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl userService;

    /** 失败计数 in-memory 模拟：key → count，便于跨调用累积。 */
    private final java.util.Map<String, String> redisStore = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> ttlStore = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        deviceMapper = mock(UserDeviceMapper.class);
        snowflake = mock(SnowflakeIdWorker.class);
        redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> vo = mock(ValueOperations.class);
        valueOps = vo;
        passwordEncoder = mock(PasswordEncoder.class);
        redisStore.clear();
        ttlStore.clear();

        // StringRedisTemplate.opsForValue() 返回 mock 的 ValueOperations
        lenient().when(redis.opsForValue()).thenReturn(valueOps);

        // get：从 in-memory 读取
        lenient().when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        // set(key, value, Duration)：写入 in-memory
        lenient().doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        // increment(key)：原子自增并返回新值；首次创建为 1
        lenient().when(valueOps.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long cur = Long.parseLong(redisStore.getOrDefault(key, "0")) + 1;
            redisStore.put(key, String.valueOf(cur));
            return cur;
        });
        // expire(key, Duration)：记录 TTL（用于断言锁定窗口）。expire 在 StringRedisTemplate 上。
        lenient().doAnswer(inv -> {
            ttlStore.put(inv.getArgument(0), ((Duration) inv.getArgument(1)).getSeconds());
            return true;
        }).when(redis).expire(anyString(), any(Duration.class));
        // delete(key)：删除
        lenient().doAnswer(inv -> {
            redisStore.remove(inv.getArgument(0));
            return true;
        }).when(redis).delete(anyString());
        // hasKey(key)：存在性
        lenient().when(redis.hasKey(anyString())).thenAnswer(inv -> redisStore.containsKey(inv.getArgument(0)));

        // 阈值 5，锁定 900 秒
        userService = new UserServiceImpl(
                userMapper, deviceMapper, snowflake, redis, passwordEncoder,
                TEST_SECRET,
                3600L,   // jwt.expire-sec (1h)
                2592000L, // jwt.refresh-sec (30d)
                5,        // user.login.fail-threshold
                900L      // user.login.lock-seconds
        );
    }

    // ==================== 工具：签发 token ====================

    /** 用与 UserServiceImpl 同样的 Hutool JWT 签发 refresh token。 */
    private String signRefreshToken(long userId, long iatMillis, long expMillis) {
        byte[] key = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        return JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("type", "refresh")
                .setPayload("username", "tester")
                .setIssuer("aim")
                .setIssuedAt(new Date(iatMillis))
                .setExpiresAt(new Date(expMillis))
                .setKey(key)
                .sign();
    }

    private String signAccessToken(long userId, long iatMillis, long expMillis) {
        byte[] key = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        return JWT.create()
                .setJWTId(UUID.randomUUID().toString())
                .setPayload("userId", userId)
                .setPayload("username", "tester")
                .setPayload("type", "access")
                .setIssuer("aim")
                .setIssuedAt(new Date(iatMillis))
                .setExpiresAt(new Date(expMillis))
                .setKey(key)
                .sign();
    }

    private User mockUser(long id, String username, String passwordHash) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash(passwordHash);
        u.setPhone("");
        u.setEmail("");
        return u;
    }

    // ==================== 任务4：login 防爆破 ====================

    @Nested
    @DisplayName("login 防爆破")
    class LoginBruteForceProtection {

        @Test
        @DisplayName("连续失败 5 次后第 6 次登录被锁定，抛 USER_FORBIDDEN")
        void shouldLockAfterFailuresReachThreshold() {
            String account = "alice";
            String storedHash = "$2a$10$hash";
            when(userMapper.selectOne(any())).thenReturn(mockUser(1L, account, storedHash));
            when(passwordEncoder.matches(anyString(), eq(storedHash))).thenReturn(false);

            // 前 5 次失败：每次抛 USER_PASSWORD_ERROR
            for (int i = 1; i <= 5; i++) {
                assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                        .isInstanceOf(BizException.class)
                        .extracting("code").isEqualTo(ErrorCode.USER_PASSWORD_ERROR.getCode());
            }
            // 第 6 次：已被锁定，抛 USER_FORBIDDEN，且不再查询 DB（密码校验前拦截）
            assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_FORBIDDEN.getCode());

            // 锁定后 selectOne 不应再被调用（前 5 次每次调用一次，第 6 次拦截在 DB 查询前）
            verify(userMapper, times(5)).selectOne(any());
        }

        @Test
        @DisplayName("用户不存在也计入失败计数，避免账户枚举")
        void shouldCountFailureWhenUserNotFound() {
            String account = "ghost";
            when(userMapper.selectOne(any())).thenReturn(null);

            // 5 次用户不存在
            for (int i = 1; i <= 5; i++) {
                assertThatThrownBy(() -> userService.login(new LoginReq(account, "any", null, null)))
                        .isInstanceOf(BizException.class)
                        .extracting("code").isEqualTo(ErrorCode.USER_PASSWORD_ERROR.getCode());
            }
            // 第 6 次锁定
            assertThatThrownBy(() -> userService.login(new LoginReq(account, "any", null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("成功登录清空失败计数，后续失败需重新累积满阈值才锁定")
        void shouldClearFailCountOnSuccess() {
            String account = "bob";
            String storedHash = "$2a$10$hash";
            when(userMapper.selectOne(any())).thenReturn(mockUser(2L, account, storedHash));
            when(passwordEncoder.matches(anyString(), eq(storedHash)))
                    .thenReturn(false)  // 第 1 次失败
                    .thenReturn(true)   // 第 2 次成功
                    .thenReturn(false); // 之后再次失败

            // 第 1 次失败
            assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                    .isInstanceOf(BizException.class);
            // 此时计数=1

            // 第 2 次成功登录：应删除计数 key
            LoginResp resp = userService.login(new LoginReq(account, "right", null, null));
            assertThat(resp.getUserId()).isEqualTo(2L);
            verify(redis, times(1)).delete("login_fail:" + account);
            // 计数已清零

            // 之后再失败 4 次：计数从 1 累积到 4，仍未达阈值 5，应返回密码错误
            for (int i = 1; i <= 4; i++) {
                assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                        .isInstanceOf(BizException.class)
                        .extracting("code").isEqualTo(ErrorCode.USER_PASSWORD_ERROR.getCode());
            }
            // 第 5 次失败：计数=5，达阈值，本次仍返回密码错误（锁定在"达到阈值后"的下一次请求触发）
            assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_PASSWORD_ERROR.getCode());
            // 第 6 次：已被锁定
            assertThatThrownBy(() -> userService.login(new LoginReq(account, "wrong", null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_FORBIDDEN.getCode());
        }

        @Test
        @DisplayName("首次失败时设置锁定窗口 TTL = loginLockSeconds")
        void shouldSetTtlOnFirstFailure() {
            String account = "carol";
            when(userMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> userService.login(new LoginReq(account, "any", null, null)))
                    .isInstanceOf(BizException.class);

            // 首次失败应调用 redis.expire 设置 900s TTL
            verify(redis, times(1)).expire(eq("login_fail:" + account), eq(Duration.ofSeconds(900L)));
            assertThat(ttlStore.get("login_fail:" + account)).isEqualTo(900L);
        }
    }

    // ==================== 任务3：refresh token 轮换 ====================

    @Nested
    @DisplayName("refresh token 轮换")
    class RefreshTokenRotation {

        @Test
        @DisplayName("refresh 返回新 refreshToken 且与旧不同，旧 jti 进黑名单")
        void shouldRotateRefreshTokenAndRevokeOldJti() {
            long userId = 1001L;
            long now = System.currentTimeMillis();
            String oldRefresh = signRefreshToken(userId, now - 60_000, now + 25_920_000_000L); // 旧 token，30天后过期

            RefreshTokenResponse resp = userService.refreshToken(oldRefresh);

            assertThat(resp.accessToken()).isNotBlank();
            assertThat(resp.refreshToken()).isNotBlank();
            assertThat(resp.refreshToken()).isNotEqualTo(oldRefresh);
            assertThat(resp.accessExpire()).isGreaterThan(now);
            assertThat(resp.refreshExpire()).isGreaterThan(now);

            // 旧 refreshToken 的 jti 应被加入黑名单（revoked_token:{jti}）
            long revokedCount = redisStore.keySet().stream()
                    .filter(k -> k.startsWith("revoked_token:")).count();
            assertThat(revokedCount).isEqualTo(1);

            // 旧 refreshToken 再次 refresh 应被拒绝（已在黑名单）
            assertThatThrownBy(() -> userService.refreshToken(oldRefresh))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_TOKEN_INVALID.getCode());
        }

        @Test
        @DisplayName("accessToken 不能作为 refreshToken 使用，抛 USER_TOKEN_INVALID")
        void shouldRejectAccessTokenAsRefreshToken() {
            long userId = 1002L;
            long now = System.currentTimeMillis();
            String accessToken = signAccessToken(userId, now, now + 3_600_000L);

            assertThatThrownBy(() -> userService.refreshToken(accessToken))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_TOKEN_INVALID.getCode());
        }

        @Test
        @DisplayName("签名错误的 refreshToken 抛 USER_TOKEN_INVALID")
        void shouldRejectTamperedRefreshToken() {
            String tampered = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9."
                    + "eyJ1c2VySWQiOjEsInR5cGUiOiJyZWZyZXNoIn0."
                    + "invalid-signature";
            assertThatThrownBy(() -> userService.refreshToken(tampered))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_TOKEN_INVALID.getCode());
        }
    }

    // ==================== 任务2：改密后吊销 token ====================

    @Nested
    @DisplayName("改密后吊销既有 token")
    class PasswordChangeRevocation {

        @Test
        @DisplayName("改密后用改密前签发的 refreshToken refresh 应失败（pwd_changed 拦截）")
        void shouldRejectRefreshTokenIssuedBeforePwdChange() {
            long userId = 2001L;
            long pwdChangeTime = System.currentTimeMillis();

            // 改密前签发的 refreshToken（iat 早于改密时间）
            String oldRefresh = signRefreshToken(userId, pwdChangeTime - 60_000, pwdChangeTime + 25_920_000_000L);

            // 模拟改密后写入 pwd_changed 时间戳
            redisStore.put("pwd_changed:" + userId, String.valueOf(pwdChangeTime));

            assertThatThrownBy(() -> userService.refreshToken(oldRefresh))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.USER_TOKEN_INVALID.getCode());
        }

        @Test
        @DisplayName("改密后用改密前签发的 accessToken validate 应返回 valid=false")
        void shouldRejectAccessTokenIssuedBeforePwdChange() {
            long userId = 2002L;
            long pwdChangeTime = System.currentTimeMillis();

            String oldAccess = signAccessToken(userId, pwdChangeTime - 60_000, pwdChangeTime + 3_600_000L);
            redisStore.put("pwd_changed:" + userId, String.valueOf(pwdChangeTime));

            ValidateTokenResp resp = userService.validateToken(oldAccess);
            assertThat(resp.isValid()).isFalse();
        }

        @Test
        @DisplayName("改密后用改密后签发的 accessToken validate 应返回 valid=true")
        void shouldAcceptAccessTokenIssuedAfterPwdChange() {
            long userId = 2003L;
            long pwdChangeTime = System.currentTimeMillis();

            String newAccess = signAccessToken(userId, pwdChangeTime + 60_000, pwdChangeTime + 3_600_000L);
            redisStore.put("pwd_changed:" + userId, String.valueOf(pwdChangeTime));

            ValidateTokenResp resp = userService.validateToken(newAccess);
            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("无改密记录时正常 token validate 应通过（不受影响）")
        void shouldPassValidateWithoutPwdChangeRecord() {
            long userId = 2004L;
            long now = System.currentTimeMillis();
            String access = signAccessToken(userId, now, now + 3_600_000L);

            ValidateTokenResp resp = userService.validateToken(access);
            assertThat(resp.isValid()).isTrue();
            assertThat(resp.getUserId()).isEqualTo(userId);
        }
    }

    // ==================== 任务1：register 服务层盲区校验 ====================

    @Nested
    @DisplayName("register 服务层校验")
    class RegisterValidation {

        @Test
        @DisplayName("username 超过 64 字符抛 BAD_REQUEST（避免 DB DataIntegrityViolation 落 500）")
        void shouldRejectTooLongUsername() {
            String tooLong = "a".repeat(65);
            assertThatThrownBy(() -> userService.register(
                    new RegisterReq(tooLong, "pass123", null, null, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("username 含非法字符抛 BAD_REQUEST")
        void shouldRejectInvalidUsernameCharset() {
            assertThatThrownBy(() -> userService.register(
                    new RegisterReq("bad name!", "pass123", null, null, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("phone 超过 20 字符抛 BAD_REQUEST")
        void shouldRejectTooLongPhone() {
            assertThatThrownBy(() -> userService.register(
                    new RegisterReq("validuser", "pass123", "1".repeat(21), null, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        }
    }
}
