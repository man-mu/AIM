package lanshan.manmu.conv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import lanshan.manmu.common.constant.ConvType;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationReq;
import lanshan.manmu.common.rpc.dto.conv.CreateConversationResp;
import lanshan.manmu.common.rpc.dto.conv.MarkReadReq;
import lanshan.manmu.conv.event.ConvEventPublisher;
import lanshan.manmu.conv.mapper.ConvReadSeqMapper;
import lanshan.manmu.conv.mapper.ConversationMapper;
import lanshan.manmu.conv.service.ConvService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * spec 18.2 场景 5 & 6：事务后置 + 事务回滚。
 * <p>事务后置：markRead 成功 → Redis 清零、Kafka 发事件（AFTER_COMMIT）；markRead 抛异常 → Redis 未清零、Kafka 未发事件
 * <p>事务回滚：markRead 内部异常触发事务回滚，AFTER_COMMIT 不执行
 *
 * <p>关键设计：
 * <ul>
 *   <li>@MockBean ConvReadSeqMapper：控制 upsertReadSeq 返回值/抛异常，模拟 DB 失败</li>
 *   <li>@SpyBean ConvEventPublisher：验证 publishReadUpdated 是否被调用（真实 Kafka 发送由内部 catch 兜底）</li>
 *   <li>conv_read_seqs 真实 UPSERT 验证已在 SingleChatIntegrationTest 覆盖，此处不重复</li>
 * </ul>
 */
class TransactionIntegrationTest extends ConvIntegrationTestBase {

    @Autowired ConvService convService;
    @Autowired ConversationMapper convMapper;
    @Autowired StringRedisTemplate redis;

    @MockBean ConvReadSeqMapper readSeqMapper;
    @SpyBean ConvEventPublisher eventPublisher;

    private static final long USER_A = 4001L;
    private static final long USER_B = 4002L;

    @Test
    void test_markReadSuccess_triggersRedisAndKafka() throws Exception {
        // 1. 创建单聊
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId = resp.getConversationId();

        // 2. 在 Redis 设置 B 的未读数
        String unreadKey = "aim:unread:" + USER_B + ":" + convId;
        redis.opsForValue().set(unreadKey, "5");

        // 3. 配置 mock：upsertReadSeq 返回 1（成功）
        doReturn(1).when(readSeqMapper).upsertReadSeq(anyLong(), anyLong(), anyLong(), anyLong());

        // 4. 调 markRead（事务提交后触发 AFTER_COMMIT）
        convService.markRead(new MarkReadReq(USER_B, convId, 5L));

        // 5. 等待 AFTER_COMMIT 执行
        awaitAfterCommit();

        // 6. 验证 Redis 未读数清零
        assertThat(redis.opsForValue().get(unreadKey))
                .as("markRead 成功提交后 Redis 未读数应被清零")
                .isNull();

        // 7. 验证 ConvEventPublisher.publishReadUpdated 被调用
        verify(eventPublisher).publishReadUpdated(convId, USER_B, 5L);
    }

    @Test
    void test_markReadFailure_rollsBack_noRedisNoKafka() throws Exception {
        // 1. 创建单聊
        CreateConversationResp resp = convService.createConversation(
                new CreateConversationReq(ConvType.SINGLE, USER_A, USER_B, null, null, null));
        long convId = resp.getConversationId();

        // 2. 在 Redis 设置 B 的未读数
        String unreadKey = "aim:unread:" + USER_B + ":" + convId;
        redis.opsForValue().set(unreadKey, "5");

        // 3. 配置 mock：upsertReadSeq 抛异常（模拟 DB 失败）
        doThrow(new RuntimeException("simulated DB failure"))
                .when(readSeqMapper).upsertReadSeq(anyLong(), anyLong(), anyLong(), anyLong());

        // 4. 调 markRead，应抛异常，事务回滚
        assertThatThrownBy(() -> convService.markRead(new MarkReadReq(USER_B, convId, 5L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated DB failure");

        // 5. 等待可能的 AFTER_COMMIT（不应执行）
        awaitAfterCommit();

        // 6. 验证 Redis 未读数未清零（事务回滚，AFTER_COMMIT 未执行）
        assertThat(redis.opsForValue().get(unreadKey))
                .as("markRead 事务回滚后 Redis 未读数不应被清零")
                .isEqualTo("5");

        // 7. 验证 ConvEventPublisher.publishReadUpdated 未被调用
        verify(eventPublisher, never()).publishReadUpdated(anyLong(), anyLong(), anyLong());
    }

    /** 等待 @TransactionalEventListener(AFTER_COMMIT) 执行 */
    private void awaitAfterCommit() throws Exception {
        Thread.sleep(150);
    }
}
