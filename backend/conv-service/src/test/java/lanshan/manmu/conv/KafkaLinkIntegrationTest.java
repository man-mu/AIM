package lanshan.manmu.conv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.event.MessageCreatedEvent;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationReq;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationResp;
import lanshan.manmu.conv.consumer.ConvMessageConsumer;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.model.entity.Conversation;
import lanshan.manmu.conv.service.ConvService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * spec 18.2 场景 4：Kafka 链路。
 * <p>按主人决策（直接调 consumer 方法模拟消费，不起 Kafka 容器）：
 * 构造 MessageCreatedEvent（含 preview 字段）→ 调 ConvMessageConsumer.onMessageCreated →
 * 验证 conversations.max_seq 更新（幂等 WHERE max_seq &lt; seq）
 */
class KafkaLinkIntegrationTest extends ConvIntegrationTestBase {

    @Autowired ConvService convService;
    @Autowired ConvMessageConsumer convMessageConsumer;
    @Autowired ConversationMapper convMapper;
    @Autowired ObjectMapper objectMapper;

    private static final long USER_A = 3001L;
    private static final long USER_B = 3002L;

    @Test
    void test_kafkaLinkConsume_updatesMaxSeq() throws Exception {
        // 1. 创建单聊
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId = resp.getConversationId();

        // 验证初始 max_seq=0
        assertThat(convMapper.selectById(convId).getMaxSeq()).isEqualTo(0L);

        // 2. 构造 MessageCreatedEvent（含 preview 字段）
        MessageCreatedEvent evt = new MessageCreatedEvent(
                4001L,           // messageId
                convId,          // convId
                USER_A,          // senderId
                "user",          // senderType
                1,               // msgType
                Map.of("text", "hello"),  // content
                "hello",         // preview（决策 22 透传字段）
                10L,             // seq
                0L,              // replyToMsgId
                System.currentTimeMillis()  // createdAt
        );
        String json = objectMapper.writeValueAsString(evt);

        // 3. 构造 ConsumerRecord 模拟 Kafka 消费
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "message.created", 0, 0, String.valueOf(convId), json);

        // 4. 调 consumer 消费
        convMessageConsumer.onMessageCreated(record);

        // 5. 验证 conversations.max_seq 更新（含 preview 透传）
        Conversation conv = convMapper.selectById(convId);
        assertThat(conv.getMaxSeq()).isEqualTo(10L);
        assertThat(conv.getLastMessageId()).isEqualTo(4001L);
        assertThat(conv.getLastMessagePreview()).isEqualTo("hello");
    }

    @Test
    void test_kafkaLinkConsume_idempotent_oldSeqIgnored() throws Exception {
        // 验证幂等性：旧 seq 不应覆盖新 seq（WHERE max_seq < #{seq}）
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId = resp.getConversationId();

        // 先发送 seq=20
        sendKafkaEvent(convId, 5001L, 20L, "msg-20");
        assertThat(convMapper.selectById(convId).getMaxSeq()).isEqualTo(20L);

        // 再发送 seq=10（旧 seq，应被忽略）
        sendKafkaEvent(convId, 5002L, 10L, "msg-10-should-be-ignored");
        Conversation conv = convMapper.selectById(convId);
        assertThat(conv.getMaxSeq()).isEqualTo(20L);
        assertThat(conv.getLastMessagePreview()).isEqualTo("msg-20");
    }

    private void sendKafkaEvent(long convId, long msgId, long seq, String preview) throws Exception {
        MessageCreatedEvent evt = new MessageCreatedEvent(
                msgId, convId, USER_A, "user", 1, Map.of("text", preview),
                preview, seq, 0L, System.currentTimeMillis());
        String json = objectMapper.writeValueAsString(evt);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "message.created", 0, 0, String.valueOf(convId), json);
        convMessageConsumer.onMessageCreated(record);
    }
}
