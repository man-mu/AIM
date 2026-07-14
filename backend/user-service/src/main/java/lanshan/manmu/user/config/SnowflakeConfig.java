package lanshan.manmu.user.config;

import lanshan.manmu.common.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake ID 生成器配置。
 */
@Configuration
public class SnowflakeConfig {

    private final long workerId;

    public SnowflakeConfig(@Value("${aim.snowflake.worker-id:0}") long workerId) {
        this.workerId = workerId;
    }

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId);
    }
}
