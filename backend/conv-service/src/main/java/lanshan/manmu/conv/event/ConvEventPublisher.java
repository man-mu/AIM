package lanshan.manmu.conv.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lanshan.manmu.common.constant.KafkaTopic;
import lanshan.manmu.common.event.ConversationReadUpdatedEvent;
import lanshan.manmu.common.event.MemberJoinedEvent;
import lanshan.manmu.common.event.MemberLeftEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConvEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ConvEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** 成员加入：addMembers 成功后调用（实际由 ConvEventListener 在 AFTER_COMMIT 触发） */
    public void publishMemberJoined(long convId, List<Long> userIds, long joinedBy) {
        MemberJoinedEvent evt = new MemberJoinedEvent(convId, userIds, joinedBy);
        publish(KafkaTopic.CONVERSATION_MEMBER_JOINED, convId, evt);
    }

    /** 成员离开：removeMembers 成功后调用 */
    public void publishMemberLeft(long convId, List<Long> userIds, long removedBy) {
        MemberLeftEvent evt = new MemberLeftEvent(convId, userIds, removedBy);
        publish(KafkaTopic.CONVERSATION_MEMBER_LEFT, convId, evt);
    }

    /** 已读更新：markRead 成功后调用 */
    public void publishReadUpdated(long convId, long userId, long lastReadSeq) {
        ConversationReadUpdatedEvent evt = new ConversationReadUpdatedEvent(convId, userId, lastReadSeq);
        publish(KafkaTopic.CONVERSATION_READ_UPDATED, convId, evt);
    }

    private void publish(String topic, long key, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(key), json);
            // 仅记录标识字段；事件正文（可能含聊天内容）存在隐私与日志爆炸风险，降为 DEBUG
            log.info("publish event topic={} key={}", topic, key);
            log.debug("publish event body topic={} key={} body={}", topic, key, json);
        } catch (JsonProcessingException e) {
            log.error("publish event failed topic={} key={}", topic, key, e);
            // 不抛异常：Kafka 发送失败不应影响已提交的 DB 事务
        }
    }
}
