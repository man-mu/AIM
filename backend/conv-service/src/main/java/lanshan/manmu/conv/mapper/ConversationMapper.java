package lanshan.manmu.conv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lanshan.manmu.conv.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 单聊去重查询：双 JOIN conv_members 找两个用户共同的单聊会话。
     * 依赖 idx_conv_members_pair(conv_id, user_id) 索引。
     */
    @Select("SELECT c.* FROM conversations c " +
            "JOIN conv_members m1 ON m1.conv_id = c.id AND m1.user_id = #{userId1} " +
            "JOIN conv_members m2 ON m2.conv_id = c.id AND m2.user_id = #{userId2} " +
            "WHERE c.type = 1 LIMIT 1")
    Conversation findPrivateConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * 查询用户所有会话（分页，按 max_seq DESC 排序，最新消息的会话排前面）。
     * MyBatis-Plus 分页插件自动注入 LIMIT/OFFSET。
     */
    @Select("SELECT c.* FROM conversations c " +
            "JOIN conv_members m ON m.conv_id = c.id AND m.user_id = #{userId} " +
            "ORDER BY c.max_seq DESC")
    IPage<Conversation> listUserConversations(IPage<Conversation> page, @Param("userId") Long userId);

    /**
     * 幂等更新 Last Message（spec 第 12.2 节）。
     * WHERE max_seq < #{seq} 保证只增不减，天然防重复消费（Kafka 至少一次语义）。
     *
     * @return 受影响行数：1=已更新；0=seq 落后或 conv 不存在，跳过
     */
    @Update("UPDATE conversations SET max_seq = #{maxSeq}, " +
            "last_message_id = #{lastMessageId}, last_message_preview = #{lastMessagePreview}, " +
            "updated_at = NOW() " +
            "WHERE id = #{convId} AND max_seq < #{maxSeq}")
    int updateLastMessageSeq(@Param("convId") Long convId,
                             @Param("lastMessageId") Long lastMessageId,
                             @Param("maxSeq") Long maxSeq,
                             @Param("lastMessagePreview") String lastMessagePreview);

    /**
     * 事务级 PostgreSQL advisory lock（单聊创建串行化）。
     * <p>用 {@code pg_advisory_xact_lock(bigint)}，锁在事务提交/回滚时自动释放，无需手动 unlock。
     * 用于 {@code createSingleConversation} 查重前，避免并发"先查后插"产生两条 A↔B 单聊。
     * key 由调用方用稳定哈希（min*2^32 + max）计算。
     * <p>用 {@code @Update} 而非 {@code @Select}：该函数返回 void，{@code @Select} 会尝试解析结果集
     * 报 "No constructor found in void"；DML 注解不处理结果集，适配 void 返回。
     */
    @Update("SELECT pg_advisory_xact_lock(#{key})")
    void advisoryLock(@Param("key") long key);

    /**
     * 原子自增 member_count 并校验上限（addMembers 并发安全）。
     * <p>{@code WHERE member_count + #{n} <= #{max}} 在 DB 层原子判定，避免基于事务前快照判断
     * 在并发下突破 500 上限；返回 0 行表示超限，调用方抛 {@code CONV_MEMBER_LIMIT}。
     *
     * @return 受影响行数：1=已自增；0=超上限或会话不存在
     */
    @Update("UPDATE conversations SET member_count = member_count + #{n}, updated_at = NOW() " +
            "WHERE id = #{convId} AND member_count + #{n} <= #{max}")
    int incrementMemberCount(@Param("convId") Long convId,
                             @Param("n") int n,
                             @Param("max") int max);
}
