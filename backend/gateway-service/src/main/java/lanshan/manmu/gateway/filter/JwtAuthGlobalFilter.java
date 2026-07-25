package lanshan.manmu.gateway.filter;

import cn.hutool.jwt.JWT;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lanshan.manmu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 JWT 鉴权全局过滤器（spec controller-spec.md §3）。
 * <p>职责：① 白名单放行 ② Bearer token 提取 ③ Hutool JWT 签名 + 过期校验
 * ④ Redis 黑名单查询（{@code revoked_token:{jti}}） ⑤ 注入 X-User-Id header。
 * <p>失败统一返回 401 + {@code Result.fail(UNAUTHORIZED)} JSON。
 * <p>JWT 密钥与 user-service 共享（从 Nacos {@code COMMON_GROUP/application.yml} 读取 {@code aim.jwt.secret}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单路径前缀（与前端 client.ts WHITE_LIST 对齐） */
    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/public/"
    );

    private final StringRedisTemplate redis;

    @Value("${aim.jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单放行
        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 2. 提取 Bearer token
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing or invalid Authorization header");
        }
        String token = auth.substring(7);

        try {
            // 3. JWT 签名 + 过期校验（与 user-service parseAndVerify 对齐）
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            JWT jwt = JWT.of(token).setKey(keyBytes);
            if (!jwt.verify()) {
                return unauthorized(exchange, "invalid signature");
            }
            jwt.validate(0);  // leeway=0，校验 exp/nbf/iat

            // 4. Redis 黑名单查询
            Object jtiObj = jwt.getPayload("jti");
            String jti = jtiObj == null ? null : String.valueOf(jtiObj);
            if (jti == null || jti.isEmpty() || "null".equals(jti)) {
                return unauthorized(exchange, "missing jti");
            }
            Boolean hasKey = redis.hasKey("revoked_token:" + jti);
            if (Boolean.TRUE.equals(hasKey)) {
                return unauthorized(exchange, "token revoked");
            }

            // 5. 注入 X-User-Id header
            Object userIdObj = jwt.getPayload("userId");
            if (userIdObj == null) {
                return unauthorized(exchange, "missing userId in token");
            }
            long userId = Long.parseLong(String.valueOf(userIdObj));

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            log.warn("jwt auth failed: {}", ex.getMessage());
            return unauthorized(exchange, "auth failed: " + ex.getMessage());
        }
    }

    /**
     * 返回 401 + Result.fail JSON。
     * <p>注意：Spring Cloud Gateway 是 WebFlux 反应式，不能用 @RestControllerAdvice。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> body = new Result<>(401, reason, null);
        byte[] bytes = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 高优先级（在路由转发前执行）
        return -100;
    }
}
