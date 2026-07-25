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
}
