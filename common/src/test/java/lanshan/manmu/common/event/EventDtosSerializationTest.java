package lanshan.manmu.common.event;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.fastjson2.JSON;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 事件 DTO JSON 序列化 / 反序列化一致性单测。
 */
class EventDtosSerializationTest {

    @Test
    void shouldSerializeAndDeserializeMessageCreatedEventWithSnakeCase() {
        Map<String, Object> content = new HashMap<>();
        content.put("text", "hello world");
        content.put("at_users", new long[]{1001, 1002});

        MessageCreatedEvent original = new MessageCreatedEvent();
        original.setMessageId(123456789L);
        original.setConvId(100L);
        original.setSenderId(2001L);
        original.setSenderType("user");
        original.setMsgType(1);
        original.setContent(content);
        original.setSeq(5L);
        original.setReplyToMsgId(0L);
        original.setCreatedAt(1710432000000L);

        String json = JSON.toJSONString(original);
        assertNotNull(json);

        // 验证 snake_case 字段名存在于 JSON 中
        assertTrue(json.contains("\"message_id\""), "JSON 应包含 snake_case 字段 message_id");
        assertTrue(json.contains("\"conv_id\""), "JSON 应包含 snake_case 字段 conv_id");
        assertTrue(json.contains("\"sender_id\""), "JSON 应包含 snake_case 字段 sender_id");
        assertTrue(json.contains("\"sender_type\""), "JSON 应包含 snake_case 字段 sender_type");
        assertTrue(json.contains("\"msg_type\""), "JSON 应包含 snake_case 字段 msg_type");
        assertTrue(json.contains("\"reply_to_msg_id\""), "JSON 应包含 snake_case 字段 reply_to_msg_id");
        assertTrue(json.contains("\"created_at\""), "JSON 应包含 snake_case 字段 created_at");

        // 反序列化后字段一致性验证
        MessageCreatedEvent parsed = JSON.parseObject(json, MessageCreatedEvent.class);
        assertEquals(original.getMessageId(), parsed.getMessageId());
        assertEquals(original.getConvId(), parsed.getConvId());
        assertEquals(original.getSenderId(), parsed.getSenderId());
        assertEquals(original.getSenderType(), parsed.getSenderType());
        assertEquals(original.getMsgType(), parsed.getMsgType());
        assertEquals(original.getSeq(), parsed.getSeq());
        assertEquals(original.getReplyToMsgId(), parsed.getReplyToMsgId());
        assertEquals(original.getCreatedAt(), parsed.getCreatedAt());
        assertNotNull(parsed.getContent());
        assertEquals("hello world", parsed.getContent().get("text"));
    }

    @Test
    void shouldSerializeAndDeserializeMessageRecalledEventWithSnakeCase() {
        MessageRecalledEvent original = new MessageRecalledEvent();
        original.setMessageId(999L);
        original.setConvId(200L);
        original.setUserId(3001L);

        String json = JSON.toJSONString(original);
        assertTrue(json.contains("\"message_id\""));
        assertTrue(json.contains("\"conv_id\""));
        assertTrue(json.contains("\"user_id\""));

        MessageRecalledEvent parsed = JSON.parseObject(json, MessageRecalledEvent.class);
        assertEquals(original.getMessageId(), parsed.getMessageId());
        assertEquals(original.getConvId(), parsed.getConvId());
        assertEquals(original.getUserId(), parsed.getUserId());
    }

    @Test
    void shouldSerializeAndDeserializeMessageDeletedEventWithSnakeCase() {
        MessageDeletedEvent original = new MessageDeletedEvent();
        original.setMessageId(888L);
        original.setConvId(300L);
        original.setUserId(4001L);
        original.setDeleteForAll(true);

        String json = JSON.toJSONString(original);
        assertTrue(json.contains("\"delete_for_all\""));

        MessageDeletedEvent parsed = JSON.parseObject(json, MessageDeletedEvent.class);
        assertEquals(original.getMessageId(), parsed.getMessageId());
        assertEquals(original.isDeleteForAll(), parsed.isDeleteForAll());
    }

    @Test
    void shouldSerializeAndDeserializeMemberJoinedEventWithSnakeCase() {
        MemberJoinedEvent original = new MemberJoinedEvent();
        original.setConvId(400L);
        original.setUserIds(java.util.List.of(5001L, 5002L));
        original.setJoinedBy(6001L);

        String json = JSON.toJSONString(original);
        assertTrue(json.contains("\"user_ids\""));
        assertTrue(json.contains("\"joined_by\""));

        MemberJoinedEvent parsed = JSON.parseObject(json, MemberJoinedEvent.class);
        assertEquals(original.getConvId(), parsed.getConvId());
        assertEquals(original.getUserIds().size(), parsed.getUserIds().size());
        assertEquals(original.getJoinedBy(), parsed.getJoinedBy());
    }
}
