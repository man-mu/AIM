package lanshan.manmu.friend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoReq;
import lanshan.manmu.common.rpc.dto.user.BatchGetUserInfoResp;
import lanshan.manmu.common.rpc.dto.user.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * friend-service 集成测试基类（与 conv-service ConvIntegrationTestBase 同款模式）。
 * <p>
 * - @SpringBootTest：加载完整 Spring 上下文
 * - @ActiveProfiles("test")：激活 application-test.yml，排除 Dubbo 自动配置、禁用 Nacos
 * - @MockBean UserRpcService：mock Dubbo 引用 bean（构造器注入后 Spring 直接注入构造器）
 * - @DynamicPropertySource：注入 PG 容器 JDBC URL（currentSchema=friend）和 Redis 容器地址
 * - @BeforeEach：清理 friend schema 4 张表 + Redis + 恢复默认用户 stub（容器单例模式下测试之间共享）
 *
 * <p>容器生命周期：单例模式手动启动，所有测试类共享同一容器实例（同 ConvIntegrationTestBase）。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class FriendIntegrationTestBase {

    /** PG 容器单例：所有测试类共享，JVM 退出时自动关闭 */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("aim")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("schema-friend.sql");

    /** Redis 容器单例 */
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        // 手动启动，不被 JUnit 5 Testcontainers 扩展管理（避免测试类之间关闭）
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // currentSchema=friend 让 PostgreSQL 默认 search_path 指向 friend schema
        r.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url.contains("?") ? url + "&currentSchema=friend" : url + "?currentSchema=friend";
        });
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** mock Dubbo UserRpcService 引用（@MockBean 替换 @Bean @DubboReference ReferenceBean） */
    @MockBean
    protected UserRpcService userRpcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        // 1. 清理 friend schema 所有表（容器单例模式下测试之间共享数据库）
        jdbcTemplate.execute("TRUNCATE TABLE friend.friends, friend.friend_groups, " +
                "friend.friend_requests, friend.user_blocks");
        // 2. 清理 Redis（测试之间共享 Redis 容器）
        redis.getConnectionFactory().getConnection().flushDb();
        // 3. 默认 stub：任意 userIds 都返回用户（id=传入值, username="u<id>", avatar=""），各测试可覆盖
        when(userRpcService.batchGetUserInfo(any(BatchGetUserInfoReq.class))).thenAnswer(inv -> {
            BatchGetUserInfoReq req = inv.getArgument(0);
            List<UserInfo> users = req.getUserIds().stream().map(FriendIntegrationTestBase::userInfo).toList();
            return new BatchGetUserInfoResp(users);
        });
    }

    protected static UserInfo userInfo(long id) {
        return new UserInfo(id, "u" + id, "", "", "", 0, "", 0L, 0L, 0L, BigDecimal.ZERO);
    }
}
