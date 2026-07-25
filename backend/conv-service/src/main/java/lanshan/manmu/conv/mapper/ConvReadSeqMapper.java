package lanshan.manmu.conv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.conv.model.entity.ConvReadSeq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConvReadSeqMapper extends BaseMapper<ConvReadSeq> {

    /**
     * UPSERT 已读位置（PG 原生语法，并发安全）。
     * last_read_seq 只增不减：用 GREATEST 保证不会回退。
     */
    @Update("INSERT INTO conv_read_seqs (id, conv_id, user_id, last_read_seq, read_at) " +
            "VALUES (#{id}, #{convId}, #{userId}, #{lastReadSeq}, NOW()) " +
            "ON CONFLICT (conv_id, user_id) DO UPDATE " +
            "SET last_read_seq = GREATEST(conv_read_seqs.last_read_seq, EXCLUDED.last_read_seq), " +
            "    read_at = NOW()")
    int upsertReadSeq(@Param("id") Long id, @Param("convId") Long convId,
                      @Param("userId") Long userId, @Param("lastReadSeq") Long lastReadSeq);
}
