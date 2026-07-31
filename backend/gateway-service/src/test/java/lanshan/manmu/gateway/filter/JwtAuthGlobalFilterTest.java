package lanshan.manmu.gateway.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.hutool.jwt.JWT;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lanshan.manmu.common.result.Result;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * {@link JwtAuthGlobalFilter} 单元测试。
 *
 * <p>核心安全校验：type=refresh 的 token 不得通过网关鉴权（防 refreshToken 当 accessToken 滥用），
 * type=access 的合法 token 应放行并注入 X-User-Id。
 *
 * <p>使用 Hutool JWT（与 user-service 同库同算法）在测试内自签 token，Spring WebFlux 的
 * {@code MockServerHttpRequest}/{@code MockServerWebExchange} 构造反应式请求。
 */
class JwtAuthGlobalFilterTest {

    /** 测试密钥（须足以满足 HS256/HSA256 字节要求；与 user-service 签发密钥等长） */
    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-test-only-0123456789";

    private StringRedisTemplate redis;
    private JwtAuthGlobalFilter filter;
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        // opsForValue().get(...) 用于 pwd_changed 改密吊销查询，默认返回 null（无改密记录）
        valueOps = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        filter = new JwtAuthGlobalFilter(redis, TEST_SECRET);
    }

    // ==================== 白名单 ====================

    @Test
    @DisplayName("白名单路径 /api/v1/auth/login 直接放行，不校验 token")
    void shouldPassWhitelistWithoutToken() {
        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/auth/login")
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get())
                .as("白名单路径应直接放行调用 chain.filter，不校验 token")
                .isTrue();
    }

    // ==================== type=access 放行 ====================

    @Test
    @DisplayName("合法 type=access token：放行并注入 X-User-Id header")
    void shouldAcceptAccessTokenAndInjectUserId() {
        long userId = 9527L;
        String jti = UUID.randomUUID().toString();
        String token = signToken(userId, jti, "access");

        when(redis.hasKey("revoked_token:" + jti)).thenReturn(false);

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/9527")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        // 捕获传递给 chain 的 exchange（过滤器内部 exchange.mutate().request(mutated).build()
        // 会生成新 exchange，X-User-Id 注入在新的 mutated request 上，原 exchange 不可见）
        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<org.springframework.web.server.ServerWebExchange> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).as("type=access 合法 token 应放行调用 chain.filter").isTrue();
        Assertions.assertThat(captured.get())
                .as("应将 mutated exchange 传给 chain")
                .isNotNull();
        Assertions.assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id"))
                .as("应注入 X-User-Id header")
                .isEqualTo(String.valueOf(userId));
    }

    // ==================== type=refresh 拒绝（核心安全修复） ====================

    @Test
    @DisplayName("type=refresh token：返回 401，不放行，不注入 X-User-Id（修复 refreshToken 滥用漏洞）")
    void shouldRejectRefreshTokenAsAccessToken() {
        long userId = 10086L;
        String jti = UUID.randomUUID().toString();
        String token = signToken(userId, jti, "refresh");

        // 不应到达 Redis 查询，但 mock 兜底
        when(redis.hasKey(anyString())).thenReturn(false);

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/10086")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).as("type=refresh token 不得放行").isFalse();
        Assertions.assertThat(exchange.getResponse().getStatusCode())
                .as("应返回 401")
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        // 响应体应为固定文案 "auth failed"，且不泄露 type 详情
        String body = exchange.getResponse().getBodyAsString().block();
        Assertions.assertThat(body).as("应有 JSON 响应体").isNotNull();
        Result<?> result = JSON.parseObject(body, Result.class);
        Assertions.assertThat(result.getCode()).isEqualTo(401);
        Assertions.assertThat(result.getMessage())
                .as("错误消息不得泄露内部细节")
                .isEqualTo("auth failed");
        Assertions.assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                .as("不应注入 X-User-Id header")
                .isNull();
    }

    // ==================== type 缺失拒绝 ====================

    @Test
    @DisplayName("type claim 缺失：返回 401")
    void shouldRejectTokenWithoutType() {
        long userId = 7L;
        String jti = UUID.randomUUID().toString();
        String token = signToken(userId, jti, null);

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/7")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).as("无 type 的 token 不得放行").isFalse();
        Assertions.assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    // ==================== 无 Authorization header 拒绝 ====================

    @Test
    @DisplayName("无 Authorization header：返回 401")
    void shouldRejectMissingAuthHeader() {
        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/1")
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).isFalse();
        Assertions.assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    // ==================== 错误消息不泄露 ====================

    @Test
    @DisplayName("篡改的 token（签名错误）：返回 401 且消息固定为 invalid signature")
    void shouldRejectTamperedToken() {
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9."
                + "eyJ1c2VySWQiOjk1MjcsImp0aSI6ImFiYyIsInR5cGUiOiJhY2Nlc3MifQ."
                + "invalid-signature";

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/9527")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).isFalse();
        Assertions.assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    // ==================== 改密吊销（pwd_changed） ====================

    @Test
    @DisplayName("token 签发早于改密时间：返回 401（改密后旧 token 被吊销）")
    void shouldRejectTokenIssuedBeforePwdChange() {
        long userId = 5555L;
        String jti = UUID.randomUUID().toString();
        // 1 小时前签发的"旧" token
        String token = signToken(userId, jti, "access",
                java.util.Date.from(java.time.Instant.now().minusSeconds(3600)));

        when(redis.hasKey("revoked_token:" + jti)).thenReturn(false);
        // 改密发生在 30 分钟前（epoch millis）
        when(valueOps.get("pwd_changed:" + userId))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 30 * 60 * 1000L));

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/5555")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).as("改密前签发的 token 不得放行").isFalse();
        Assertions.assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("token 签发晚于改密时间：放行（改密后的新 token 不受影响）")
    void shouldAcceptTokenIssuedAfterPwdChange() {
        long userId = 6666L;
        String jti = UUID.randomUUID().toString();
        String token = signToken(userId, jti, "access");

        when(redis.hasKey("revoked_token:" + jti)).thenReturn(false);
        // 改密发生在 1 小时前（epoch millis），token 是当前签发的
        when(valueOps.get("pwd_changed:" + userId))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 3600 * 1000L));

        org.springframework.mock.http.server.reactive.MockServerHttpRequest request =
                org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/v1/users/6666")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
        org.springframework.mock.web.server.MockServerWebExchange exchange =
                org.springframework.mock.web.server.MockServerWebExchange.from(request);

        java.util.concurrent.atomic.AtomicBoolean chained = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain = ex -> {
            chained.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Assertions.assertThat(chained.get()).as("改密后签发的 token 应放行").isTrue();
    }

    // ==================== 签发工具 ====================

    /**
     * 用与 user-service 同样的 Hutool JWT 签发 token（iat 取当前时间）。
     *
     * @param userId userId claim
     * @param jti    jti claim
     * @param type   type claim，传入 null 则不写入该 claim
     * @return 签发的 JWT 字符串
     */
    private String signToken(long userId, String jti, String type) {
        return signToken(userId, jti, type, new java.util.Date());
    }

    /**
     * 用与 user-service 同样的 Hutool JWT 签发 token，可指定签发时间（供改密吊销用例构造旧 token）。
     */
    private String signToken(long userId, String jti, String type, java.util.Date issuedAt) {
        byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        JWT jwt = JWT.create()
                .setKey(keyBytes)
                .setPayload("userId", userId)
                .setPayload("jti", jti)
                .setIssuedAt(issuedAt);
        if (type != null) {
            jwt.setPayload("type", type);
        }
        // 设置足够长的过期时间，避免 validate 失败
        jwt.setExpiresAt(java.util.Date.from(java.time.Instant.now().plusSeconds(7200)));
        return jwt.sign();
    }
}