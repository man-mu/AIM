package lanshan.manmu.conv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lanshan.manmu.conv.model.entity.ConvSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConvSettingsMapper extends BaseMapper<ConvSettings> {

    /**
     * UPSERT 用户会话设置。
     * 用 COALESCE 处理「null=不更新」语义（Boolean 包装类型）。
     */
    @Update("INSERT INTO conv_settings (id, conv_id, user_id, is_muted, is_pinned) " +
            "VALUES (#{id}, #{convId}, #{userId}, " +
            "        COALESCE(#{isMuted}, false), COALESCE(#{isPinned}, false)) " +
            "ON CONFLICT (conv_id, user_id) DO UPDATE " +
            "SET is_muted  = COALESCE(#{isMuted},  conv_settings.is_muted), " +
            "    is_pinned = COALESCE(#{isPinned}, conv_settings.is_pinned)")
    int upsertSettings(@Param("id") Long id, @Param("convId") Long convId,
                       @Param("userId") Long userId,
                       @Param("isMuted") Boolean isMuted, @Param("isPinned") Boolean isPinned);
}
