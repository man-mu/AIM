package lanshan.manmu.conv.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lanshan.manmu.common.event.MessageCreatedEvent;
import lanshan.manmu.common.rpc.dto.conv.UpdateLastMessageReq;
import lanshan.manmu.conv.service.ConvService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * ConvMessageConsumer 单测（spec 第 18.1 节）。
 * <p>覆盖：正常消费 → 透传 preview → 调 updateLastMessage；解析失败/DB 异常 → 向上抛异常，
 * 交由 Spring Kafka 的 {@code DefaultErrorHandler}（见 {@code KafkaConsumerConfig}）做
 * FixedBackOff 重试，超限后投递 DLQ。消费端幂等由 updateLastMessage 内部 WHERE max_seq&lt;seq 保证。
 */
class ConvMessageConsumerTest {

    private ConvService convService;
    private ObjectMapper objectMapper;
    private ConvMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        convService = Mockito.mock(ConvService.class);
        objectMapper = new ObjectMapper();  // 真实 ObjectMapper，验证 JSON 解析
        consumer = new ConvMessageConsumer(convService, objectMapper);
    }

    @Test
    void onMessageCreated_normal_transparentPreviewAndCallUpdateLastMessage() throws Exception {
        // 构造 MessageCreatedEvent，preview 由 message-service 生成（决策 22）
        MessageCreatedEvent evt = new MessageCreatedEvent(
                9001L,         // messageId
                1001L,         // convId
                2001L,         // senderId
                "user",        // senderType
                1,             // msgType
                Map.of("text", "hello"),  // content
                "[图片]",      // preview（透传，决策 22）
                100L,          // seq
                0L,            // replyToMsgId
                System.currentTimeMillis()  // createdAt
        );
        String json = objectMapper.writeValueAsString(evt);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("message.created", 0, 0, "1001", json);

        consumer.onMessageCreated(record);

        // 验证透传 preview 并调 updateLastMessage
        ArgumentCaptor<UpdateLastMessageReq> captor = ArgumentCaptor.forClass(UpdateLastMessageReq.class);
        verify(convService).updateLastMessage(captor.capture());
        UpdateLastMessageReq req = captor.getValue();
        assertEquals(1001L, req.getConversationId());
        assertEquals(9001L, req.getLastMessageId());
        assertEquals(100L, req.getMaxSeq());
        assertEquals("[图片]", req.getLastMessagePreview(), "preview 应透传 event 里的值");
    }

    @Test
    void onMessageCreated_nullPreview_transparentAsNull() throws Exception {
        // preview 为 null（message-service 未生成，决策 9 风险点 9：Phase 1 接受 null preview）
        MessageCreatedEvent evt = new MessageCreatedEvent(
                9002L, 1002L, 2002L, "user", 1, Map.of("text", "hi"),
                null,   // preview null
                50L, 0L, System.currentTimeMillis());
        String json = objectMapper.writeValueAsString(evt);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("message.created", 0, 0, "1002", json);

        consumer.onMessageCreated(record);

        ArgumentCaptor<UpdateLastMessageReq> captor = ArgumentCaptor.forClass(UpdateLastMessageReq.class);
        verify(convService).updateLastMessage(captor.capture());
        assertNull(captor.getValue().getLastMessagePreview(), "null preview 透传为 null");
    }

    @Test
    void onMessageCreated_invalidJson_propagateExceptionNoCall() {
        // 解析失败向上抛（交由 DefaultErrorHandler 重试 → DLQ），且不应调 updateLastMessage
        ConsumerRecord<String, String> record = new ConsumerRecord<>("message.created", 0, 0, "1001", "not-a-json");

        assertThrows(Exception.class, () -> consumer.onMessageCreated(record));

        // 解析失败时不应调 updateLastMessage
        verify(convService, never()).updateLastMessage(any());
    }

    @Test
    void onMessageCreated_updateLastMessageThrows_propagateException() throws Exception {
        // convService.updateLastMessage 抛异常时，consumer 应向上抛出（交给 DefaultErrorHandler 重试/DLQ）
        MessageCreatedEvent evt = new MessageCreatedEvent(
                9003L, 1003L, 2003L, "user", 1, Map.of(), "preview", 30L, 0L, System.currentTimeMillis());
        String json = objectMapper.writeValueAsString(evt);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("message.created", 0, 0, "1003", json);
        doThrow(new RuntimeException("db error")).when(convService).updateLastMessage(any());

        // 抛出异常（由 DefaultErrorHandler 接管重试/DLQ）
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> consumer.onMessageCreated(record));
        assertEquals("db error", thrown.getMessage());
        verify(convService).updateLastMessage(any());
    }
}
