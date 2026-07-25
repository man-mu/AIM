package lanshan.manmu.conv;

import lanshan.manmu.common.rpc.UserRpcService;
import lanshan.manmu.conv.service.impl.ConvServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * conv-service 集成测试基类（spec 第 18.3 节）。
 * <p>
 * - @SpringBootTest：加载完整 Spring 上下文
 * - @ActiveProfiles("test")：激活 application-test.yml，排除 Dubbo 自动配置、禁用 Nacos/Kafka auto-startup
 * - @MockBean UserRpcService：mock Dubbo 引用，避免连真实 Nacos 找 Provider
 * - @DynamicPropertySource：注入 PG 容器 JDBC URL 和 Redis 容器地址
 * - @BeforeEach：排除 Dubbo 后 @DubboReference 字段为 null，手动注入 mock 到 ConvServiceImpl
 *
 * <p>容器生命周期：用单例模式手动启动，所有测试类共享同一容器实例，避免 JUnit 5 @Container
 * 在测试类之间关闭容器导致 Spring ApplicationContext 缓存引用失效端口的问题。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class ConvIntegrationTestBase {

    /** PG 容器单例：所有测试类共享，JVM 退出时自动关闭 */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("aim")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("schema-conv.sql");

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
        // currentSchema=conv 让 PostgreSQL 默认 search_path 指向 conv schema
        r.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url.contains("?") ? url + "&currentSchema=conv" : url + "?currentSchema=conv";
        });
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockBean
    protected UserRpcService userRpcService;

    @Autowired
    private ConvServiceImpl convServiceImpl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        // 1. 清理 conv schema 所有表（容器单例模式下测试之间共享数据库）
        jdbcTemplate.execute("TRUNCATE TABLE conv.conversations, conv.conv_members, " +
                "conv.conv_read_seqs, conv.conv_settings, conv.conv_bots");
        // 2. 清理 Redis（测试之间共享 Redis 容器）
        redis.getConnectionFactory().getConnection().flushDb();
        // 3. 排除 Dubbo 自动配置后，@DubboReference UserRpcService 字段为 null
        // 手动注入 @MockBean 创建的 mock 到 ConvServiceImpl
        ReflectionTestUtils.setField(convServiceImpl, "userRpcService", userRpcService);
    }
}
