package lanshan.manmu.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 * <p>启动时自动检查 bucket 是否存在，不存在则创建。
 * <p>bucket 初始化失败抛 {@link IllegalStateException} 阻止 Spring 启动——fail fast。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 启动时检查 bucket，不存在则创建
        try {
            if (!client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' 创建成功", bucket);
            } else {
                log.info("MinIO bucket '{}' 已存在", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败，bucket={}, endpoint={}", bucket, endpoint, e);
            throw new IllegalStateException("MinIO 初始化失败", e);
        }

        return client;
    }
}