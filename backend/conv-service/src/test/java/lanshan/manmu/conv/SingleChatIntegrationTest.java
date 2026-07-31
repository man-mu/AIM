package lanshan.manmu.conv;

import static org.assertj.core.api.Assertions.assertThat;

import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.constant.MemberRole;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationReq;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationResp;
import lanshan.manmu.common.rpc.dto.conv.MarkReadReq;
import lanshan.manmu.common.rpc.dto.conv.UpdateLastMessageReq;
import lanshan.manmu.conv.mapper.ConvReadSeqMapper;
import lanshan.manmu.conv.mapper.ConversationMemberMapper;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.model.entity.ConvReadSeq;
import lanshan.manmu.conv.model.entity.Conversation;
import lanshan.manmu.conv.model.entity.ConversationMember;
import lanshan.manmu.conv.service.ConvService;
import lanshan.manmu.conv.util.UnreadCacheService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * spec 18.2 场景 1 & 3：单聊端到端 + 单聊去重。
 * <p>单聊端到端：A 创建→B 入群→A 发消息→B markRead→Redis 清零→已读位置 UPSERT
 * <p>单聊去重：A↔B 第二次创建返回同一 convId
 */
class SingleChatIntegrationTest extends ConvIntegrationTestBase {

    @Autowired ConvService convService;
    @Autowired ConversationMapper convMapper;
    @Autowired ConversationMemberMapper memberMapper;
    @Autowired ConvReadSeqMapper readSeqMapper;
    @Autowired StringRedisTemplate redis;
    @Autowired UnreadCacheService unreadCache;

    private static final long USER_A = 1001L;
    private static final long USER_B = 1002L;

    @Test
    void test_singleChatEndToEnd() throws Exception {
        // 1. A 创建与 B 的单聊（A、B 自动入群，memberCount=2）
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId = resp.getConversationId();
        assertThat(convId).isGreaterThan(0);

        // 验证会话基本信息
        Conversation conv = convMapper.selectById(convId);
        assertThat(conv.getType()).isEqualTo(ConvType.SINGLE);
        assertThat(conv.getMemberCount()).isEqualTo(2);

        // 验证 A、B 都是 MEMBER（单聊无 OWNER）
        ConversationMember memberA = findMember(convId, USER_A);
        ConversationMember memberB = findMember(convId, USER_B);
        assertThat(memberA.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(memberB.getRole()).isEqualTo(MemberRole.MEMBER);

        // 2. A 发消息（模拟 message-service 调 updateLastMessage）
        long msgId = 2001L;
        long seq = 5L;
        convService.updateLastMessage(new UpdateLastMessageReq(convId, msgId, seq, "hello from A"));

        // 验证 conversations.max_seq 更新
        Conversation updated = convMapper.selectById(convId);
        assertThat(updated.getMaxSeq()).isEqualTo(seq);
        assertThat(updated.getLastMessageId()).isEqualTo(msgId);
        assertThat(updated.getLastMessagePreview()).isEqualTo("hello from A");

        // 3. 在 Redis 设置 B 的未读数（模拟 message-service 投递时累加）
        String unreadKey = "aim:unread:" + USER_B + ":" + convId;
        redis.opsForValue().set(unreadKey, "3");
        assertThat(redis.opsForValue().get(unreadKey)).isEqualTo("3");

        // 4. B markRead（lastReadSeq=5）
        convService.markRead(new MarkReadReq(USER_B, convId, seq));

        // 5. 验证 Redis 未读数清零（AFTER_COMMIT 执行 clearUnreadCount）
        // @TransactionalEventListener(AFTER_COMMIT) 在事务提交后异步执行，需短暂等待
        awaitAfterCommit();

        assertThat(redis.opsForValue().get(unreadKey))
                .as("markRead 提交后 Redis 未读数应被清零")
                .isNull();

        // 6. 验证 conv_read_seqs UPSERT（B 的已读位置=5）
        ConvReadSeq readSeq = findReadSeq(convId, USER_B);
        assertThat(readSeq).isNotNull();
        assertThat(readSeq.getLastReadSeq()).isEqualTo(seq);
    }

    @Test
    void test_singleChatDedup() {
        // 第一次创建 A↔B 单聊
        CreateConversationResp resp1 = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId1 = resp1.getConversationId();

        // 第二次创建（B 发起）应返回同一 convId
        CreateConversationResp resp2 = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_B, USER_A, null, null, null));
        long convId2 = resp2.getConversationId();

        assertThat(convId2).isEqualTo(convId1);

        // 验证不会创建新的会话记录
        assertThat(convMapper.selectCount(null)).isEqualTo(1);
        assertThat(memberMapper.selectCount(null)).isEqualTo(2);
    }

    private ConversationMember findMember(long convId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConvId, convId)
                .eq(ConversationMember::getUserId, userId));
    }

    private ConvReadSeq findReadSeq(long convId, long userId) {
        return readSeqMapper.selectOne(new LambdaQueryWrapper<ConvReadSeq>()
                .eq(ConvReadSeq::getConvId, convId)
                .eq(ConvReadSeq::getUserId, userId));
    }

    /** 等待 @TransactionalEventListener(AFTER_COMMIT) 异步执行 */
    private void awaitAfterCommit() throws Exception {
        // AFTER_COMMIT 在事务提交后同步执行（默认），但跨线程边界，短暂 sleep 确保 Redis 操作完成
        Thread.sleep(100);
    }
}
