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
 *   <li>幂等性由 updateLastMessage 内部 WHERE max_seq &lt; #{seq} 保证</li>
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
        } catch (Exception e) {
            log.error("consume message.created failed record={}", record.value(), e);
            // Phase 1 不做重试，等后续引入 DLQ（见 KafkaTopic.MESSAGE_CREATED_DLQ）
        }
    }
}
