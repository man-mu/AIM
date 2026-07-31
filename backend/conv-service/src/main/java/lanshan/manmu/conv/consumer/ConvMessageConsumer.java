package lanshan.manmu.conv.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lanshan.manmu.common.constant.KafkaTopic;
import lanshan.manmu.common.event.MessageCreatedEvent;
import lanshan.manmu.common.rpc.dto.conv.UpdateLastMessageReq;
import lanshan.manmu.conv.service.ConvService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * conv-service 唯一 Kafka 消费者：消费 message.created 触发 updateLastMessage。
 * <p>关键设计（spec 第 16 节）：
 * <ul>
 *   <li>@KafkaListener 不指定 groupId —— 由 Nacos 配置 spring.kafka.consumer.group-id: conv-service 提供（决策 17）</li>
 *   <li>透传 event 里的 preview 字段（决策 22，不跨服务猜测 content 结构）</li>
 *   <li>UpdateLastMessageReq 用 new 构造（无 @Builder，字段名见 common 第 2.6 节）</li>
 *   <li>幂等性由 updateLastMessage 内部 WHERE max_seq &lt; #{seq} 保证，故失败重试是安全的</li>
 *   <li>消费失败不再吞异常：抛出后由 Spring Kafka 的 {@code DefaultErrorHandler}
 *       （见 {@code KafkaConsumerConfig}）做固定退避重试，超限后投递 DLQ
 *       （{@link KafkaTopic#MESSAGE_CREATED_DLQ}）</li>
 * </ul>
 */
@Component
@Slf4j
public class ConvMessageConsumer {

    private final ConvService convService;
    private final ObjectMapper objectMapper;

    public ConvMessageConsumer(ConvService convService, ObjectMapper objectMapper) {
        this.convService = convService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopic.MESSAGE_CREATED)
    // 注：不指定 groupId —— 由 Nacos 配置 spring.kafka.consumer.group-id: conv-service 提供（决策 17）
    public void onMessageCreated(ConsumerRecord<String, String> record) {
        // try-catch 仅做日志归档；任何异常一律向上抛，交由 KafkaConsumerConfig 配置的
        // DefaultErrorHandler（FixedBackOff 1000ms × 3）重试，重试耗尽后投递 message.created.dlq。
        // 消费端幂等由 updateLastMessage 内部 WHERE max_seq < seq 保证，重试安全。
        try {
            MessageCreatedEvent evt = objectMapper.readValue(record.value(), MessageCreatedEvent.class);
            log.info("consume message.created convId={} seq={}", evt.getConvId(), evt.getSeq());

            // 用 new 构造（UpdateLastMessageReq 无 @Builder，字段名见 common 第 2.6 节）
            UpdateLastMessageReq req = new UpdateLastMessageReq(
                    evt.getConvId(),
                    evt.getMessageId(),
                    evt.getSeq(),
                    evt.getPreview()   // 由 message-service 生成（决策 22），conv-service 只透传
            );
            convService.updateLastMessage(req);
        } catch (RuntimeException | Error e) {
            // 仅记录标识字段（topic/partition/offset/key，key 即 convId）；
            // 事件正文（含聊天内容）存在隐私与日志爆炸风险，降为 DEBUG。
            log.error("consume message.created failed topic={} partition={} offset={} key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), e);
            log.debug("consume message.created failed raw payload topic={} partition={} offset={} value={}",
                    record.topic(), record.partition(), record.offset(), record.value());
            // 不再吞异常：原样向上抛（运行时异常无需声明 throws），交给 DefaultErrorHandler 重试，超限写 DLQ
            throw e;
        } catch (Exception e) {
            // 主要是 JsonProcessingException（受检异常），日志归档后包成运行时异常向上抛，
            // 让 DefaultErrorHandler 接管重试/DLQ，避免污染 @KafkaListener 方法签名。
            // 仅记录标识字段；事件正文降为 DEBUG（含聊天内容，避免隐私泄漏与日志爆炸）。
            log.error("consume message.created failed topic={} partition={} offset={} key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), e);
            log.debug("consume message.created failed raw payload topic={} partition={} offset={} value={}",
                    record.topic(), record.partition(), record.offset(), record.value());
            throw new RuntimeException("consume message.created failed", e);
        }
    }
}
