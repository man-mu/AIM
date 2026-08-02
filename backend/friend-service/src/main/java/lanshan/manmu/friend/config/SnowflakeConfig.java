package lanshan.manmu.friend.config;

import lanshan.manmu.common.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake ID 生成器配置（friend-service workerId=1，见 docs/sql/init/nacos-init-data.sql）。
 * <p>不加 @RefreshScope：workerId 是启动期固定值，运行时变更会导致 ID 重复。
 * 与 user-service / conv-service / file-service 保持一致。
 */
@Configuration
public class SnowflakeConfig {

    private final long workerId;

    public SnowflakeConfig(@Value("${aim.snowflake.worker-id:1}") long workerId) {
        this.workerId = workerId;
    }

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId);
    }
}
