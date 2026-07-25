package lanshan.manmu.conv.config;

import lanshan.manmu.common.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake ID 生成器配置（conv-service workerId=3）。
 * <p>不加 @RefreshScope：workerId 是启动期固定值，运行时变更会导致 ID 重复。
 * 与 user-service / file-service 保持一致。
 */
@Configuration
public class SnowflakeConfig {

    private final long workerId;

    public SnowflakeConfig(@Value("${aim.snowflake.worker-id:3}") long workerId) {
        this.workerId = workerId;
    }

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker() {
        return new SnowflakeIdWorker(workerId);
    }
}
