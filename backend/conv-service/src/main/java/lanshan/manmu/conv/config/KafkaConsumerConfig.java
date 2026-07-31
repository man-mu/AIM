package lanshan.manmu.conv.config;

import lanshan.manmu.common.constant.KafkaTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * conv-service Kafka 消费侧错误处理配置。
 *
 * <p>解决问题：原 {@code ConvMessageConsumer} 在消费失败时吞掉全部异常，配合 Nacos 未显式关闭的
 * auto-commit，DB 瞬时故障时消息 offset 照常提交，导致 {@code message.created} 永久丢失、
 * 会话 last_message_id/max_seq/preview 停在旧值。
 *
 * <p>本配置注册一个全局 {@link DefaultErrorHandler}（Spring Boot 3.x 会通过
 * {@code ConcurrentKafkaListenerContainerFactoryConfigurer#getIfUnique()} 自动装配进
 * {@code @KafkaListener} 默认监听器工厂），提供：
 * <ul>
 *   <li>逐次重试：{@link FixedBackOff} 1000ms × 3 次，覆盖 DB 瞬时故障（连接抖动等）；</li>
 *   <li>退避耗尽后进入 DLQ：由 {@link DeadLetterPublishingRecoverer} 把原始记录投递到
 *       {@link KafkaTopic#MESSAGE_CREATED_DLQ}，避免消息永久丢失，便于人工/离线补偿。</li>
 * </ul>
 *
 * <p>幂等性保证：消费端 {@code updateLastMessage} 内部以
 * {@code WHERE max_seq < seq} 做乐观条件更新，重试与重复消费均安全，因此不需要在 handler 侧
 * 做额外去重。
 *
 * <p>事务一致性：
 * <ul>
 *   <li>Nacos 配置 {@code spring.kafka.consumer.enable-auto-commit=false} +
 *       {@code spring.kafka.listener.ack-mode=record}：每条记录处理成功后才提交 offset；</li>
 *   <li>本 {@link DefaultErrorHandler} 默认 {@code seekAfterError=true}、{@code ackAfterHandle=true}，
 *       重试失败的标准记录会在进入 DLQ 后被 ack，不会无限阻塞分区。</li>
 * </ul>
 *
 * <p>注意：{@link KafkaTemplate} 复用 {@code KafkaProducerConfig} 暴露的非事务模板（无
 * {@code transactionIdPrefix}），DLQ 发送走普通 producer，不在消费者事务内。
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * FixedBackOff 退避间隔（毫秒）。
     */
    private static final long RETRY_INTERVAL_MS = 1000L;

    /**
     * FixedBackOff 最大重试次数（不含首次执行，即总共最多 {@code 1 + 3 = 4} 次尝试后写 DLQ）。
     */
    private static final long MAX_RETRY_ATTEMPTS = 3L;

    /**
     * 消费侧的错误处理器：固定退避重试 + DLQ 兜底。
     *
     * <p>{@link DefaultErrorHandler} 是 Spring Kafka 3.x 推荐替代 {@code SeekToCurrentErrorHandler}
     * 的实现，作为唯一的 {@link org.springframework.kafka.listener.CommonErrorHandler} Bean，
     * 会被 Spring Boot Kafka 自动配置自动注入到 {@code @KafkaListener} 默认监听器工厂。
     *
     * @param kafkaTemplate {@code KafkaProducerConfig.kafkaTemplate()} 暴露的 KafkaTemplate，
     *                      用于 {@link DeadLetterPublishingRecoverer} 投递 DLQ 消息
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // DeadLetterPublishingRecoverer 把失败记录投递到 DLQ：topic = DLQ 常量、分区沿用原 partition
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopic.MESSAGE_CREATED_DLQ, record.partition()));
        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_ATTEMPTS);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // 明确开启默认行为：进入 DLQ 后视为该记录已补偿，正常 ack 并推进 offset
        handler.setAckAfterHandle(true);
        return handler;
    }
}